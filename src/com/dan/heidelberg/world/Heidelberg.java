package com.dan.heidelberg.world;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.Mesh;
import com.dan.heidelberg.core.MeshBuilder;
import com.dan.heidelberg.core.Scene;
import com.dan.heidelberg.core.Terrain;

/**
 * Baut die Welt von Heidelberg: Gelände (vier Gitter), Neckar, Altstadt in einfachen Blöcken mit
 * Heiliggeistkirche und Alter Brücke, den Platzhalter fürs Schloss, den Laubwald auf den Hängen von
 * Schlossberg, Königstuhl und Heiligenberg, dazu Wiese und Wege. Stand: 1619 (Entscheidung 1).
 */
public final class Heidelberg {
    private Heidelberg() { }

    /** Marktplatz mit der Heiliggeistkirche (genähert) und Alte Brücke (Lage am Fluss). */
    public static final double[] CHURCH = {-545, -318};
    public static final double BRIDGE_X = -440;
    /** Blickziele: Schloss (Hof), Königstuhl, Heiligenberg, Alte Brücke (x, z). */
    public static final double[] SCHLOSS = {0, 0}, KOENIGSTUHL = {782, 961}, HEILIGENBERG = {-655, -2163};

    /** Prüfstand statt Schloss (Phase 2): Probesaal und Probebecken auf dem Schlosshof. */
    public static final boolean PROBE = Boolean.getBoolean("heidelberg.probe");

    public static World build() {
        return build(PROBE);
    }

    public static World build(boolean probe) {
        long t0 = System.nanoTime();
        java.util.List<float[]> dust = new java.util.ArrayList<>();
        Terrain t = new Terrain();
        if (!probe) com.dan.heidelberg.castle.Hortus.carve(t);
        // Platz fürs Schloss: auf dem Schlosshof auf Höhe 0 geebnet (im Terrain), Wege formen den Boden
        Roadways rw = new Roadways(t);
        t.build();
        rw.attach();
        MeshBuilder mb = new MeshBuilder();
        mb.nearX0 = -1900; mb.nearX1 = 1900; mb.nearZ0 = -1900; mb.nearZ1 = 1900;
        grid(mb, t, t.castle, true);
        grid(mb, t, t.fine, true);
        grid(mb, t, t.mid, true);
        grid(mb, t, t.far, false);
        river(mb, t);
        java.util.Random rnd = new java.util.Random(1619);
        double[] br = Town.bridge(mb, t, BRIDGE_X);
        Town.church(mb, t, CHURCH[0], CHURCH[1]);
        if (probe) com.dan.heidelberg.castle.Probe.build(mb, dust);
        else {
            com.dan.heidelberg.castle.Castle.build(mb, dust);
            com.dan.heidelberg.castle.Hortus.build(mb, t, new java.util.Random(1620));
            com.dan.heidelberg.castle.Wasserweg.build(mb, t);
        }
        java.util.List<double[]> free = new java.util.ArrayList<>();
        free.add(new double[]{CHURCH[0], CHURCH[1], 48});
        free.add(new double[]{br[0] + br[2] * -60, br[1] + br[3] * -60, 34});
        free.add(new double[]{-30, 40, 190});
        free.addAll(com.dan.heidelberg.castle.Wasserweg.KEEP);
        int houses = Town.houses(mb, t, rw, rnd, free.toArray(new double[0][]));
        java.util.List<double[]> chimneys = new java.util.ArrayList<>(Town.CHIMNEYS);
        com.dan.heidelberg.castle.Torches torches = new com.dan.heidelberg.castle.Torches();
        if (!probe) torches.build(mb, t);
        Grove grove = new Grove();
        int trees = forest(mb, t, rnd, grove, rw);
        Sward sward = new Sward(t, rw);
        Neckar neckar = new Neckar(t);
        // Zeitschalter (Phase 9): die drei Ruinenstufen kommen als Letztes in den Baukasten, damit das Netz von 1619 keine Ecke zu viel hat
        int nvA = mb.vertexCount();
        int[] nvEnd = null;
        if (!probe) {
            nvEnd = com.dan.heidelberg.castle.Ruin.buildAll(mb, dust);
            System.out.printf("Ruinenstufen: %,d Ecken, %,d Dreiecke zusammen%n", nvEnd[3] - nvA, mb.triCount());
        }
        Mesh base = mb.build(64, MeshBuilder.ERA_1619, nvA, null);
        Scene sc = new Scene("Heidelberg 1619", base, t, null);
        grove.attach(sc.mesh);
        sc.trees = trees;
        sc.snags = houses;
        System.out.printf("Aufbau: %.1f s, %,d Dreiecke, %,d Bäume, %,d Häuser%n", (System.nanoTime() - t0) / 1e9, sc.triangles(), trees, houses);
        World wd = new World(sc, grove, neckar, sward, rw);
        wd.builder = mb;
        wd.stageEnds = nvEnd;
        long ts = System.nanoTime();
        wd.solids = new com.dan.heidelberg.core.Solids(sc.mesh);
        System.out.printf("Feste Körper: %,d Dreiecke in %,d Zellen, %.1f s%n", wd.solids.triangles, wd.solids.cells, (System.nanoTime() - ts) / 1e9);
        wd.townChimneys = chimneys;
        wd.torches = torches;
        wd.keepSolids();
        wd.life.attach(wd.solids, sc.terrain);
        wd.dust = dust.toArray(new float[0][]);
        return wd;
    }

    // ------------------------------------------------------------ Wald

    /**
     * Laubwald auf den Hängen: Buchen und Eichen, mit der Höhe mehr Nadelbäume (Waldkiefer, Fichte,
     * Weißtanne), an steilen Hängen Douglasien (die es um 1619 noch nicht gab, hier als Platzhalter für
     * Nadelbäume mit schmaler Krone). Bäume stehen nur in der Nähe des Schlosses einzeln (bis rund
     * 1,5 km); weiter weg trägt die Bodenfarbe den Wald. Liefert die Zahl der Bäume.
     */
    static int forest(MeshBuilder mb, Terrain t, java.util.Random rnd, Grove grove, Roadways rw) {
        double keepEdge = mb.maxEdge;
        mb.maxEdge = 1e9;
        float[] gm = new float[5];
        int trees = 0;
        double step = 11;
        double R = 1500;
        for (double z = -R; z <= R; z += step) {
            for (double x = -R; x <= R; x += step) {
                double ds = Math.hypot(x, z);
                if (ds > R) continue;
                // mit dem Abstand dünner (je 1 von 3 ab 900 m): die Ferne tragen die Bodenfarbe und die Stämme in der Nähe
                double keep = ds < 700 ? 1 : Math.max(0.18, 1 - (ds - 700) / 900);
                if (rnd.nextDouble() > keep) continue;
                double px = x + (rnd.nextDouble() - 0.5) * step * 0.9, pz = z + (rnd.nextDouble() - 0.5) * step * 0.9;
                if (rw != null && rw.clearance(px, pz) < 4) continue;
                if (com.dan.heidelberg.castle.Castle.zone(px, pz) > 0.02f) continue;
                t.ground((float) px, (float) pz, gm);
                float fo = gm[4], bank = gm[0];
                if (fo < 0.5f || bank < 12) continue;
                float e = t.forestEdge(px, pz);
                if (e < 0) continue;
                if (rnd.nextDouble() > 0.92 - e / 500.0) continue;
                double y = t.sample(px, pz);
                double slope = Math.hypot(t.sample(px + 3, pz) - y, t.sample(px, pz + 3) - y) / 3;
                // Höhe über dem Talboden: oben mehr Nadelholz
                double up = Math.max(0, Math.min(1, (y + 70) / 380));
                double r = rnd.nextDouble();
                boolean near = e < 7 && ds < 160;
                if (slope > 0.55 && r < 0.3) grove.conifer(mb, Grove.Conifer.DOUGLAS, px, y, pz, rnd, near);
                else if (r < 0.10 + 0.55 * up * up) {
                    double q = rnd.nextDouble();
                    if (q < 0.45) grove.pine(mb, px, y, pz, rnd, near);
                    else if (q < 0.75) grove.conifer(mb, Grove.Conifer.FIR, px, y, pz, rnd, near);
                    else grove.conifer(mb, Grove.Conifer.SPRUCE, px, y, pz, rnd, near);
                } else grove.broadleaf(mb, px, y, pz, rnd, ds < 260 && e < 16 ? 2 : 3);
                trees++;
            }
        }
        mb.maxEdge = keepEdge;
        mb.swayFn = null;
        mb.swayValue = 0;
        return trees;
    }

    // ------------------------------------------------------------ Gelände und Fluss

    /** Ein Höhengitter als Dreiecke, ohne die Löcher; mit skirt hängt am Außenrand ein Saum gegen Ritzen. */
    private static void grid(MeshBuilder mb, Terrain t, Terrain.Grid g, boolean skirt) {
        int w = g.nx + 1;
        float[] sky = skyOf(t, g);
        mb.skyFn = (x, y, z, nx, ny, nz) -> {
            int i = (int) Math.round((x - g.x0) / g.cell), j = (int) Math.round((z - g.z0) / g.cell);
            return sky[Math.max(0, Math.min(g.nz, j)) * w + Math.max(0, Math.min(g.nx, i))];
        };
        int[] id = new int[w * (g.nz + 1)];
        java.util.Arrays.fill(id, -1);
        for (int j = 0; j < g.nz; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (g.inHole(i, j)) continue;
                int a = node(mb, g, id, i, j), b = node(mb, g, id, i + 1, j), c = node(mb, g, id, i + 1, j + 1), d = node(mb, g, id, i, j + 1);
                mb.tri(a, b, c, Mat.TERRAIN);
                mb.tri(a, c, d, Mat.TERRAIN);
            }
        }
        // Säume an den Lochrändern: wo das feinere Gitter tiefer liegt, schließt dieser Saum den Spalt
        int keep0 = mb.group;
        mb.group = 1;
        for (int j = 0; j < g.nz; j++) {
            for (int i = 0; i < g.nx; i++) {
                if (g.inHole(i, j)) continue;
                if (i + 1 < g.nx && g.inHole(i + 1, j)) holeSkirt(mb, g, id, i + 1, j, i + 1, j + 1);
                if (i > 0 && g.inHole(i - 1, j)) holeSkirt(mb, g, id, i, j, i, j + 1);
                if (j + 1 < g.nz && g.inHole(i, j + 1)) holeSkirt(mb, g, id, i, j + 1, i + 1, j + 1);
                if (j > 0 && g.inHole(i, j - 1)) holeSkirt(mb, g, id, i, j, i + 1, j);
            }
        }
        mb.group = keep0;
        if (!skirt) { mb.skyFn = null; return; }
        // Saum: am Außenrand senkrecht (12 m + 30 % der Zellgröße) hinunter, von beiden Seiten sichtbar
        int keep = mb.group;
        mb.group = 1;
        for (int side = 0; side < 4; side++) {
            int n = side < 2 ? g.nx : g.nz;
            for (int k = 0; k < n; k++) {
                int i0, j0, i1, j1;
                switch (side) {
                    case 0: i0 = k; j0 = 0; i1 = k + 1; j1 = 0; break;
                    case 1: i0 = k; j0 = g.nz; i1 = k + 1; j1 = g.nz; break;
                    case 2: i0 = 0; j0 = k; i1 = 0; j1 = k + 1; break;
                    default: i0 = g.nx; j0 = k; i1 = g.nx; j1 = k + 1;
                }
                int a = node(mb, g, id, i0, j0), b = node(mb, g, id, i1, j1);
                int p = j0 * w + i0, q = j1 * w + i1;
                int c = mb.v(g.x0 + i1 * g.cell, g.h[q] - (12 + 0.3 * g.cell), g.z0 + j1 * g.cell, g.nxs[q], g.nys[q], g.nzs[q]);
                int d = mb.v(g.x0 + i0 * g.cell, g.h[p] - (12 + 0.3 * g.cell), g.z0 + j0 * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
                mb.tri(a, b, c, Mat.TERRAIN);
                mb.tri(a, c, d, Mat.TERRAIN);
            }
        }
        mb.group = keep;
        mb.skyFn = null;
    }

    /** Senkrechter Saum (12 m + 30 % der Zellgröße) hinunter entlang der Kante (i0,j0)–(i1,j1). */
    private static void holeSkirt(MeshBuilder mb, Terrain.Grid g, int[] id, int i0, int j0, int i1, int j1) {
        int w = g.nx + 1;
        int a = node(mb, g, id, i0, j0), b = node(mb, g, id, i1, j1);
        int p = j0 * w + i0, q = j1 * w + i1;
        int c = mb.v(g.x0 + i1 * g.cell, g.h[q] - (12 + 0.3 * g.cell), g.z0 + j1 * g.cell, g.nxs[q], g.nys[q], g.nzs[q]);
        int d = mb.v(g.x0 + i0 * g.cell, g.h[p] - (12 + 0.3 * g.cell), g.z0 + j0 * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
        mb.tri(a, b, c, Mat.TERRAIN);
        mb.tri(a, c, d, Mat.TERRAIN);
    }

    private static int node(MeshBuilder mb, Terrain.Grid g, int[] id, int i, int j) {
        int p = j * (g.nx + 1) + i;
        if (id[p] < 0) id[p] = mb.v(g.x0 + i * g.cell, g.h[p], g.z0 + j * g.cell, g.nxs[p], g.nys[p], g.nzs[p]);
        return id[p];
    }

    /** Himmelssicht der Geländeknoten; das Raster wird parallel vorgerechnet. */
    private static float[] skyOf(Terrain t, Terrain.Grid g) {
        int w = g.nx + 1;
        float[] s = new float[w * (g.nz + 1)];
        java.util.stream.IntStream.range(0, g.nz + 1).parallel().forEach(j -> {
            for (int i = 0; i < w; i++) s[j * w + i] = t.skyView(g.x0 + i * g.cell, g.z0 + j * g.cell, g.h[j * w + i]);
        });
        return s;
    }

    /** Der Fluss als Wasserband auf Höhe des Wasserspiegels; wo der Boden höher liegt, hebt sich das Band. */
    private static void river(MeshBuilder mb, Terrain t) {
        int n = t.riverPoints();
        int[] prev = null;
        for (int i = 0; i < n; i++) {
            double x = t.riverX(i), z = t.riverZ(i);
            if (x < t.mid.x0 + 10 || x > t.mid.x1() - 10 || z < t.mid.z0 + 10 || z > t.mid.z1() - 10) { prev = null; continue; }
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            double dx = t.riverX(b) - t.riverX(a), dz = t.riverZ(b) - t.riverZ(a);
            double l = Math.hypot(dx, dz);
            double px = -dz / l, pz = dx / l;
            double half = t.riverHalf(i) * 1.25;
            double y = t.riverLevel(i);
            double lift = Math.max(0, t.sample(x, z) + 0.3 - y);
            y += lift;
            int[] cur = {
                    mb.v(x + px * half, y, z + pz * half, 0, 1, 0),
                    mb.v(x, y, z, 0, 1, 0),
                    mb.v(x - px * half, y, z - pz * half, 0, 1, 0)};
            if (prev != null) {
                for (int k = 0; k < 2; k++) {
                    mb.tri(prev[k], cur[k], cur[k + 1], Mat.RIVER);
                    mb.tri(prev[k], cur[k + 1], prev[k + 1], Mat.RIVER);
                }
            }
            prev = cur;
        }
    }

}
