package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;
import com.dan.heidelberg.core.Terrain;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Hortus Palatinus nach Salomon de Caus (1614–1619), Stand des Stichwerks von 1620: Terrassen mit
 * Stützmauern, Bogenbau mit Großer und Kleiner Grotte, Pyramidentreppe, Knotenfelder aus Buchsbaum und
 * farbigem Kies, achteckiges Becken, Rheusbecken, Säulenbrunnen, Pomeranzenbäume in Erdkästen und das
 * Elisabethentor. Was nach den Quellen nur geplant und nie gebaut wurde, trägt das Material
 * {@link Mat#PLANNED} (kühl getönt, schaltbar im Bedienfeld).
 * <p>Lage und Höhen sind <b>geschätzt</b>: die Quellen nennen die Stufen (obere Terrasse 8,5 m breit und 2,5 m
 * über der mittleren, untere 6 m tiefer), nicht die Lage zum Schloss. Der Garten liegt hier südwestlich
 * des Schlosses am Hang (im Plan L-förmig um das Schloss).
 */
public final class Hortus {
    private Hortus() { }

    /** Terrassenhöhen über dem Hof: Koniferenterrasse, Hauptterrasse, Obere Terrasse (2,5 m über der Hauptterrasse). */
    public static final double KL = -2.0, ML = 3.5, UL = 6.0;
    /** Rechtecke (x0, z0, x1, z1) der Terrassen. */
    static final double[] K1 = {-132, 62, -8, 84}, K2 = {-132, 8, -76, 62}, M = {-132, 84, -8, 120}, U = {-132, 120, -8, 129};
    static final double[][] ALL = {U, M, K1, K2};
    static final double[] LEVEL = {UL, ML, KL, KL};

    /** Grotten und Becken: Mitte der Großen Grotte (x), Kleine Grotte (x). */
    static final double GROSS_X = -70, KLEIN_X = -106;

    /** Ebnet das Gelände unter den Terrassen (höchste zuerst, damit die tieferen die Ränder der höheren überschreiben). */
    public static void carve(Terrain t) {
        for (int i = 0; i < ALL.length; i++) t.carve(ALL[i][0], ALL[i][1], ALL[i][2], ALL[i][3], LEVEL[i] - 0.9, 3);
        // Grottenräume: das Gelände darüber nicht als Wand in den Raum ragen lassen (zuletzt, tiefste Ebene)
        t.carve(GROSS_X - 13, 84, GROSS_X + 13, 113, KL - 0.05, 1);   // eine Gitterzelle (4 m) breiter als der Raum, sonst steigt das Gelände an den Wänden in den Raum
        t.carve(KLEIN_X - 10.5, 84, KLEIN_X + 10.5, 99, KL - 0.05, 1);
        // Dach der Grottenräume: wer höher als die Decke steht, steht auf der Hauptterrasse
        t.pit(GROSS_X - 13, 84, GROSS_X + 13, 113, ML - 1.0, ML);
        t.pit(KLEIN_X - 10.5, 84, KLEIN_X + 10.5, 99, ML - 1.0, ML);
    }

    /** 1 auf den Terrassen, weicher Rand von 10 m. */
    public static float zone(double x, double z) {
        double best = 0;
        for (double[] r : ALL) {
            double dx = Math.max(Math.max(r[0] - x, x - r[2]), 0), dz = Math.max(Math.max(r[1] - z, z - r[3]), 0);
            best = Math.max(best, 1 - Castle.smooth(0, 12, Math.hypot(dx, dz)));
        }
        return (float) best;
    }

    // ------------------------------------------------------------ Aufbau

    public static void build(MeshBuilder mb, Terrain t, Random rnd) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.skyFn = null;
        floors(mb);
        walls(mb, t);
        bogenbau(mb);
        pyramidentreppe(mb);
        knotenfelder(mb);
        becken(mb);
        pomeranzen(mb, rnd);
        koniferen(mb, rnd);
        elisabethentor(mb);
        obereTerrasse(mb, rnd);
        Castle.place("Hortus Palatinus – Hauptterrasse", -70, ML, 102, "E");
        Castle.place("Große Grotte", GROSS_X, KL, 90, "E");
        Castle.place("Kleine Grotte", KLEIN_X, KL, 90, "E");
        Castle.place("Elisabethentor", -104, KL, 8, "E");
        Castle.place("Pyramidentreppe", -70, ML, 116, "E");
        mb.maxEdge = keep;
    }

    // ------------------------------------------------------------ Böden

    static void floors(MeshBuilder mb) {
        // Hauptterrasse und obere Terrasse: Kies (Wege und Beete), Koniferenterrasse und Westgarten: Rasen mit Kieswegen
        mb.rectH(M[0], M[1], M[2], M[3], ML, true, Mat.GRAVEL);
        mb.rectH(U[0], U[1], U[2], U[3], UL, true, Mat.GRAVEL);
        mb.rectH(K1[0], K1[1], K1[2], K1[3], KL, true, Mat.LAWN);
        mb.rectH(K2[0], K2[1], K2[2], K2[3], KL, true, Mat.LAWN);
        // Kieswege in K1 und K2 (0,02 m über dem Rasen)
        double y = KL + 0.025;
        mb.rectH(-72, 62, -68, 84, y, true, Mat.GRAVEL);         // Mittelweg auf die Große Grotte zu
        mb.rectH(-132, 71, -8, 74, y, true, Mat.GRAVEL);         // Querweg
        mb.rectH(-106, 8, -102, 62, y, true, Mat.GRAVEL);        // Weg vom Elisabethentor
        mb.rectH(-132, 33, -76, 36, y, true, Mat.GRAVEL);
        // Platz um das Achteck: Pflaster
        mb.rectH(-82, 91, -58, 113, ML + 0.03, true, Mat.SANDSTONE);
    }

    // ------------------------------------------------------------ Stützmauern

    /**
     * Mauer entlang einer Kante der Terrasse: orient 'x' läuft entlang x bei z = fixed, 'z' entlang z bei x = fixed,
     * out = ±1 nach außen. Oben bis zum Gelände dahinter (Einschnitt) oder mindestens 0,9 m über dem Boden (Brüstung).
     */
    static void edge(MeshBuilder mb, Terrain t, char orient, double fixed, double a, double b, int out, double level) {
        double th = 1.0;
        for (double s = a; s < b - 1e-6; s += 4) {
            double e = Math.min(b, s + 4), mid = (s + e) / 2;
            double ox = orient == 'x' ? mid : fixed + out * 3.5, oz = orient == 'x' ? fixed + out * 3.5 : mid;
            double ground = t.sample(ox, oz);
            double top = Math.max(level + 0.9, ground + 0.3);
            double bot = Math.min(level - 12, ground - 2);
            boolean parapet = top <= level + 0.95;
            if (orient == 'x') {
                double z0 = out > 0 ? fixed : fixed - th, z1 = out > 0 ? fixed + th : fixed;
                mb.box(s, bot, z0, e, top, z1, Mat.SANDSTONE, false);
                if (parapet) mb.box(s, top, z0 - 0.15, e, top + 0.22, z1 + 0.15, Mat.STATUE, true);
            } else {
                double x0 = out > 0 ? fixed : fixed - th, x1 = out > 0 ? fixed + th : fixed;
                mb.box(x0, bot, s, x1, top, e, Mat.SANDSTONE, false);
                if (parapet) mb.box(x0 - 0.15, top, s, x1 + 0.15, top + 0.22, e, Mat.STATUE, true);
            }
        }
    }

    static void walls(MeshBuilder mb, Terrain t) {
        // K1: Nord (Einschnitt zum Schlosshof), Ost, West
        edge(mb, t, 'x', K1[1], -76, K1[2], -1, KL);
        edge(mb, t, 'z', K1[2], K1[1], K1[3], +1, KL);
        edge(mb, t, 'z', K1[0], K1[1], K1[3], -1, KL);
        // K2: West, Ost, Nord (ohne Tor)
        edge(mb, t, 'z', K2[0], K2[1], K2[3], -1, KL);
        edge(mb, t, 'z', K2[2], K2[1], K2[3], +1, KL);
        edge(mb, t, 'x', K2[1], K2[0], -112, -1, KL);
        edge(mb, t, 'x', K2[1], -96, K2[2], -1, KL);
        // M: Ost; U: West, Ost, Süd (Einschnitt)
        edge(mb, t, 'z', M[2], M[1], M[3], +1, ML);
        edge(mb, t, 'z', U[0], U[1], U[3], -1, UL);
        edge(mb, t, 'z', U[2], U[1], U[3], +1, UL);
        edge(mb, t, 'x', U[3], U[0], U[2], +1, UL);
    }

    /** Wand mit Blick nach Norden (−z) bei z von x = xa bis xb im Wandrahmen; Bögen als {Mitte, Breite, Höhe}. */
    static void northWall(MeshBuilder mb, double z, double xa, double xb, double yb, double yt, double T, List<double[]> arches, boolean hollow, int mat, double archY0, double[][] ribs) {
        int v0 = mb.vertexCount();
        List<Arch.Op> ops = new ArrayList<>();
        for (double[] a : arches) ops.add(Arch.arch(a[0], archY0, a[1], a[2]));
        MeshBuilder.SkyFn keepFn = mb.skyFn;
        if (hollow) mb.skyFn = (x, y, zz, nx, ny, nz) -> zz < -0.25 ? 0.25f : 1f;
        Arch.wall(mb, xb - xa, yb, yt, T, ops, mat, Mat.STATUE, hollow);
        mb.skyFn = keepFn;
        if (ribs != null) for (double[] r : ribs) Arch.rib(mb, r[0], r[1], r[2], r[3], r[4], Mat.STATUE);
        // Nordwand: Außenseite zeigt nach −z (yaw π), u wächst nach −x
        mb.transform(v0, Math.PI, xb, 0, z);
    }

    // ------------------------------------------------------------ Bogenbau und Grotten

    static void bogenbau(MeshBuilder mb) {
        double z = M[1];
        double yb = KL - 7, yt = ML;
        double[][] ribs = new double[][]{{0, M[2] - M[0], ML - 0.45, ML + 0.0, 0.3}};
        // Blendarkaden: Bögen von 5 m, alle 11 m; Lücken für die Grotten
        double[] seg = {M[0], KLEIN_X - 7, KLEIN_X + 7, GROSS_X - 9, GROSS_X + 9, M[2]};
        // 1 Blendarkaden M[0]..KLEIN-7
        niches(mb, z, seg[0], seg[1], yb, yt);
        // Kleine Grotte (Eingang 4,2 m)
        List<double[]> aK = new ArrayList<>();
        aK.add(new double[]{(seg[2] - seg[1]) / 2, 4.2, 4.2});
        northWall(mb, z, seg[1], seg[2], yb, yt, 1.4, aK, true, Mat.SANDSTONE, KL + 0.05, null);
        niches(mb, z, seg[2], seg[3], yb, yt);
        // Große Grotte (Eingang 8 m)
        List<double[]> aG = new ArrayList<>();
        aG.add(new double[]{(seg[4] - seg[3]) / 2, 8.0, 4.9});
        northWall(mb, z, seg[3], seg[4], yb, yt, 1.4, aG, true, Mat.SANDSTONE, KL + 0.05, null);
        niches(mb, z, seg[4], seg[5], yb, yt);
        grotte(mb);
        // Bogenbau an der Westseite der Hauptterrasse (Scheffelterrasse): hohe Blendbögen über dem fallenden Hang
        double x = M[0];
        int v0 = mb.vertexCount();
        List<Arch.Op> ops = new ArrayList<>();
        double L = M[3] - M[1];
        int n = 4;
        for (int i = 0; i < n; i++) ops.add(Arch.arch(L * (i + 0.5) / n, KL - 3.0, 6.0, 9.0));
        Arch.wall(mb, L, KL - 12, ML, 1.0, ops, Mat.SANDSTONE, Mat.STATUE, false);
        for (int i = 0; i <= n; i++) Arch.rib(mb, Math.max(0, L * i / n - 0.5), Math.min(L, L * i / n + 0.5), KL - 12, ML, 0.35, Mat.STATUE);
        Arch.rib(mb, 0, L, ML - 0.5, ML, 0.45, Mat.STATUE);
        // Außenseite nach −x (yaw 3π/2), u wächst nach +z
        mb.transform(v0, 3 * Math.PI / 2, x - 0.02, 0, M[1]);
        Castle.place("Scheffelterrasse / Bogenbau", x, ML, 102, "E");
    }

    /** Blendarkaden zwischen xa und xb: Bögen von 5 m Breite alle rund 11 m. */
    static void niches(MeshBuilder mb, double z, double xa, double xb, double yb, double yt) {
        double len = xb - xa;
        int n = Math.max(1, (int) Math.floor(len / 11));
        List<double[]> ar = new ArrayList<>();
        for (int i = 0; i < n; i++) ar.add(new double[]{len * (i + 0.5) / n, 5.0, 4.7});
        northWall(mb, z, xa, xb, yb, yt, 1.4, ar, false, Mat.SANDSTONE, KL + 0.05, new double[][]{{-0.2, len + 0.2, ML - 0.45, ML, 0.3}});
    }

    /** Große und Kleine Grotte hinter dem Bogenbau: Raum aus Tuff, Kaskade und Becken, zweiter Raum mit Steintisch. */
    static void grotte(MeshBuilder mb) {
        MeshBuilder.SkyFn keep = mb.skyFn;
        mb.skyFn = (x, y, z, nx, ny, nz) -> 0.18f;
        double zf = M[1] + 1.4, ztop = ML - 0.8;
        // --- Große Grotte: Halle 15,2 × 14 m, Decke 0,6 unter dem Boden der Hauptterrasse
        double gx0 = GROSS_X - 7.6, gx1 = GROSS_X + 7.6, gz1 = zf + 14;
        room(mb, gx0, zf, gx1, gz1, KL + 0.05, ztop, false);   // Rückwand: die Trennwand weiter unten
        // Kaskade an der Rückwand: drei Stufen, Wasser stufenweise tiefer, Becken davor
        for (int i = 0; i < 3; i++) {
            double zz = gz1 - 1.6 * (i + 1), h = KL + 0.05 + 1.5 - 0.5 * i;
            mb.box(GROSS_X - 5, KL, zz, GROSS_X + 5, h, zz + 1.6, Mat.ROCK, true);
            mb.rectH(GROSS_X - 4.6, zz + 0.3, GROSS_X + 4.6, zz + 1.5, h + 0.02, true, Mat.BASIN);
        }
        mb.box(GROSS_X - 5, KL, zf + 4, GROSS_X + 5, KL + 0.5, zf + 5, Mat.ROCK, false);
        mb.box(GROSS_X - 5, KL, zf + 8, GROSS_X + 5, KL + 0.5, zf + 9, Mat.ROCK, false);
        mb.box(GROSS_X - 5, KL, zf + 4, GROSS_X - 4, KL + 0.5, zf + 9, Mat.ROCK, false);
        mb.box(GROSS_X + 4, KL, zf + 4, GROSS_X + 5, KL + 0.5, zf + 9, Mat.ROCK, false);
        mb.rectH(GROSS_X - 4, zf + 5, GROSS_X + 4, zf + 8, KL + 0.38, true, Mat.BASIN);
        // --- zweiter Raum hinter der Halle, 8 × 8 m, Steintisch mit Wassermechanik, Durchgang 3 m
        double rx0 = GROSS_X - 4, rx1 = GROSS_X + 4, rz1 = gz1 + 8;
        int v0 = mb.vertexCount();
        List<Arch.Op> pass = new ArrayList<>();
        pass.add(Arch.arch(8.0 - 4.0 + 0.0, KL + 0.05, 3.0, 3.8));   // u von Osten gezählt: Mitte des 16 m breiten Segments
        // Trennwand bei z = gz1 (zeigt nach Norden), Breite 16 m von rx0-4 bis rx1+4
        List<double[]> a = new ArrayList<>();
        a.add(new double[]{8.0, 3.0, 3.8});
        int vw = mb.vertexCount();
        northWall(mb, gz1, GROSS_X - 8, GROSS_X + 8, KL - 2, ztop, 1.0, a, true, Mat.ROCK, KL + 0.05, null);
        mb.skyMul(vw, mb.vertexCount(), 0.2f);   // die Trennwand liegt im Dunkeln der Grotte
        room(mb, rx0, gz1 + 1.0, rx1, rz1, KL + 0.05, ztop);
        mb.box(GROSS_X - 1.6, KL + 0.05, gz1 + 3.6, GROSS_X + 1.6, KL + 0.85, gz1 + 5.2, Mat.STATUE, true);
        mb.box(GROSS_X - 1.9, KL + 0.85, gz1 + 3.3, GROSS_X + 1.9, KL + 1.05, gz1 + 5.5, Mat.MARBLE, true);
        // --- Kleine Grotte: Raum 11 × 9 m mit Wandbrunnen
        double kx0 = KLEIN_X - 5.6, kx1 = KLEIN_X + 5.6, kz1 = zf + 9;
        room(mb, kx0, zf, kx1, kz1, KL + 0.05, ztop);
        mb.box(KLEIN_X - 2.5, KL + 0.05, kz1 - 1.0, KLEIN_X + 2.5, KL + 1.1, kz1, Mat.ROCK, true);
        mb.rectH(KLEIN_X - 2.1, kz1 - 0.9, KLEIN_X + 2.1, kz1 - 0.1, KL + 1.12, true, Mat.BASIN);
        mb.rectH(KLEIN_X - 3, kz1 - 4, KLEIN_X + 3, kz1 - 1.6, KL + 0.4, true, Mat.BASIN);
        mb.box(KLEIN_X - 3.3, KL, kz1 - 4.3, KLEIN_X + 3.3, KL + 0.5, kz1 - 4.0, Mat.ROCK, false);
        mb.box(KLEIN_X - 3.3, KL, kz1 - 1.6, KLEIN_X + 3.3, KL + 0.5, kz1 - 1.3, Mat.ROCK, false);
        mb.skyFn = keep;
        Interior.grotten(mb, GROSS_X, KLEIN_X, zf, KL + 0.05);
        Wasserorgel.build(mb);
        mb.skyFn = keep;
    }

    /** Raum: Boden, Decke und drei Wände (die Nordseite liegt in der Fassade) aus Tuff, Innenflächen. */
    static void room(MeshBuilder mb, double x0, double z0, double x1, double z1, double yFloor, double yTop) {
        room(mb, x0, z0, x1, z1, yFloor, yTop, true);
    }

    /** Wie oben; back = false lässt die Rückwand weg, wenn dort schon eine Wand steht (sonst flimmern zwei deckungsgleiche Flächen). */
    static void room(MeshBuilder mb, double x0, double z0, double x1, double z1, double yFloor, double yTop, boolean back) {
        mb.rectH(x0, z0, x1, z1, yFloor, true, Mat.ROCK);
        mb.rectH(x0, z0, x1, z1, yTop, false, Mat.ROCK);
        mb.quad(new double[]{x0, yFloor, z0}, new double[]{x0, yFloor, z1}, new double[]{x0, yTop, z1}, new double[]{x0, yTop, z0}, 1, 0, 0, Mat.ROCK);
        mb.quad(new double[]{x1, yFloor, z0}, new double[]{x1, yFloor, z1}, new double[]{x1, yTop, z1}, new double[]{x1, yTop, z0}, -1, 0, 0, Mat.ROCK);
        if (back) mb.quad(new double[]{x0, yFloor, z1}, new double[]{x1, yFloor, z1}, new double[]{x1, yTop, z1}, new double[]{x0, yTop, z1}, 0, 0, -1, Mat.ROCK);
    }

    // ------------------------------------------------------------ Pyramidentreppe, Knotenfelder, Becken

    static void pyramidentreppe(MeshBuilder mb) {
        double cx = -70, z = U[1];
        // Mauer zwischen Hauptterrasse und oberer Terrasse (nach Norden), mit Pilastern
        int v0 = mb.vertexCount();
        double L = U[2] - U[0];
        Arch.wall(mb, L, ML - 6, UL, 1.0, new ArrayList<>(), Mat.SANDSTONE, Mat.STATUE, false);
        Arch.rib(mb, 0, L, UL - 0.4, UL, 0.3, Mat.STATUE);
        for (double u = 4; u < L; u += 11) Arch.rib(mb, u - 0.4, u + 0.4, ML, UL - 0.4, 0.25, Mat.STATUE);
        mb.transform(v0, Math.PI, U[2], 0, z);
        // Pyramide: vier Stufen, nach oben schmaler (nur geplant: de Caus 1620)
        for (int i = 0; i < 4; i++) {
            double w = 16 - 3.2 * i, d = 8.0 - 1.7 * i;
            mb.box(cx - w / 2, ML, z - d, cx + w / 2, ML + 0.625 * (i + 1), z, Mat.PLANNED, i == 0);
        }
    }

    /** Band (Buchsbaum) entlang eines Linienzugs: Breite w, Höhe h über y. */
    static void ribbon(MeshBuilder mb, double[][] pts, double y, double w, double h, int mat) {
        for (int i = 0; i + 1 < pts.length; i++) {
            double ax = pts[i][0], az = pts[i][1], bx = pts[i + 1][0], bz = pts[i + 1][1];
            double dx = bx - ax, dz = bz - az, l = Math.hypot(dx, dz);
            if (l < 1e-6) continue;
            double nx = -dz / l * w / 2, nz = dx / l * w / 2;
            double[] a0 = {ax + nx, y, az + nz}, a1 = {ax - nx, y, az - nz}, b0 = {bx + nx, y, bz + nz}, b1 = {bx - nx, y, bz - nz};
            double[] a0t = {a0[0], y + h, a0[2]}, a1t = {a1[0], y + h, a1[2]}, b0t = {b0[0], y + h, b0[2]}, b1t = {b1[0], y + h, b1[2]};
            mb.quad(a0t, b0t, b1t, a1t, 0, 1, 0, mat);
            mb.quad(a0, b0, b0t, a0t, nx, 0, nz, mat);
            mb.quad(a1, b1, b1t, a1t, -nx, 0, -nz, mat);
        }
    }

    static double[][] circle(double cx, double cz, double r, int n, double a0, double a1) {
        double[][] p = new double[n + 1][];
        for (int i = 0; i <= n; i++) {
            double a = a0 + (a1 - a0) * i / n;
            p[i] = new double[]{cx + r * Math.cos(a), cz + r * Math.sin(a)};
        }
        return p;
    }

    /** Ein Knotenfeld (Teppichbeet) der Größe w × d bei (x0, z0): Rand, Muster nach Nummer, Kies darin. */
    static void knot(MeshBuilder mb, double x0, double z0, double w, double d, int pattern) {
        double y = ML + 0.03, cx = x0 + w / 2, cz = z0 + d / 2, bw = 0.55, bh = 0.45;
        mb.rectH(x0, z0, x0 + w, z0 + d, y, true, Mat.GRAVEL);
        ribbon(mb, new double[][]{{x0, z0}, {x0 + w, z0}, {x0 + w, z0 + d}, {x0, z0 + d}, {x0, z0}}, y, bw, bh, Mat.HEDGE);
        double r = Math.min(w, d) / 2 - 1.0;
        switch (pattern % 5) {
            case 0:
                ribbon(mb, circle(cx, cz, r * 0.55, 32, 0, 2 * Math.PI), y, bw, bh, Mat.HEDGE);
                for (int k = 0; k < 4; k++) {
                    double sx = (k % 2 == 0 ? -1 : 1), sz = (k < 2 ? -1 : 1);
                    double ccx = cx + sx * (w / 2 - 1), ccz = cz + sz * (d / 2 - 1);
                    double a0 = Math.atan2(-sz, -sx) - Math.PI / 4;
                    ribbon(mb, circle(ccx, ccz, 3.0, 12, a0, a0 + Math.PI / 2), y, bw, bh, Mat.HEDGE);
                }
                break;
            case 1:
                ribbon(mb, new double[][]{{cx - w / 2 + 1, cz}, {cx, cz - d / 2 + 1}, {cx + w / 2 - 1, cz}, {cx, cz + d / 2 - 1}, {cx - w / 2 + 1, cz}}, y, bw, bh, Mat.HEDGE);
                ribbon(mb, circle(cx, cz, r * 0.4, 24, 0, 2 * Math.PI), y, bw, bh, Mat.HEDGE);
                break;
            case 2:
                for (int s = -1; s <= 1; s += 2) {
                    double[][] p = new double[25][];
                    for (int i = 0; i <= 24; i++) {
                        double u = i / 24.0;
                        p[i] = new double[]{x0 + 1 + (w - 2) * u, cz + s * 0.2 * d + Math.sin(u * 2 * Math.PI * 2) * d * 0.18};
                    }
                    ribbon(mb, p, y, bw, bh, Mat.HEDGE);
                }
                break;
            case 3:
                ribbon(mb, new double[][]{{x0 + 1, z0 + 1}, {x0 + w - 1, z0 + d - 1}}, y, bw, bh, Mat.HEDGE);
                ribbon(mb, new double[][]{{x0 + w - 1, z0 + 1}, {x0 + 1, z0 + d - 1}}, y, bw, bh, Mat.HEDGE);
                for (int k = 0; k < 4; k++) ribbon(mb, circle(cx + (k % 2 == 0 ? -1 : 1) * w * 0.27, cz + (k < 2 ? -1 : 1) * d * 0.22, 1.4, 12, 0, 2 * Math.PI), y, bw, bh, Mat.HEDGE);
                break;
            default:
                ribbon(mb, new double[][]{{x0 + 1.2, z0 + 1.2}, {x0 + w - 1.2, z0 + 1.2}, {x0 + w - 1.2, z0 + d - 1.2}, {x0 + 1.2, z0 + d - 1.2}, {x0 + 1.2, z0 + 1.2}}, y, bw, bh, Mat.HEDGE);
                ribbon(mb, circle(cx, cz, r * 0.5, 24, 0, 2 * Math.PI), y, bw, bh, Mat.HEDGE);
                ribbon(mb, circle(cx, cz, r * 0.25, 16, 0, 2 * Math.PI), y, bw, bh, Mat.HEDGE);
        }
        // Kugeln aus Buchsbaum an den Ecken
        for (int k = 0; k < 4; k++) mb.ellipsoid(x0 + (k % 2) * w, y + 0.5, z0 + (k / 2) * d, 0.55, 0.55, 0.55, 10, 6, Mat.HEDGE);
    }

    static void knotenfelder(MeshBuilder mb) {
        double[] cols = {-128, -106, -54, -32};
        double[] rows = {87.5, 104.5};
        int p = 0;
        for (int j = 0; j < rows.length; j++) for (int i = 0; i < cols.length; i++) knot(mb, cols[i], rows[j], 20, 13, p++ + j);
        // Säulenbrunnen im Zentrum des ersten Feldes (nach den Quellen: im Zentrum eines Knotenfeldes)
        double cx = cols[0] + 10, cz = rows[1] + 6.5;
        Arch.roundBasin(mb, cx, cz, ML, ML + 0.55, ML + 0.4, 1.9, 0.4, 20, Mat.PLANNED, Mat.BASIN);
        Arch.column(mb, cx, cz, ML + 0.1, ML + 4.2, 0.28, Mat.MARBLE);
        Arch.roundBasin(mb, cx, cz, ML + 4.2, ML + 4.55, ML + 4.4, 0.7, 0.15, 12, Mat.MARBLE, Mat.BASIN);
        Castle.place("Säulenbrunnen", cx, ML, cz, "E");
    }

    static void becken(MeshBuilder mb) {
        // achteckiges Becken mit Fratzengesichtern: Mitte der Hauptterrasse (nur geplant)
        double cx = -70, cz = 102;
        Arch.roundBasin(mb, cx, cz, ML, ML + 0.7, ML + 0.5, 5.2, 0.7, 8, Mat.PLANNED, Mat.BASIN);
        for (int i = 0; i < 8; i++) {
            double a = (i + 0.0) * Math.PI / 4;
            double rr = 5.9;
            mb.box(cx + rr * Math.cos(a) - 0.3, ML + 0.7, cz + rr * Math.sin(a) - 0.3, cx + rr * Math.cos(a) + 0.3, ML + 1.2, cz + rr * Math.sin(a) + 0.3, Mat.STATUE, true);
        }
        mb.cylinder(cx, cz, ML + 0.5, ML + 1.4, 0.5, 0.35, 12, Mat.PLANNED, true);   // Sockel für die Fontäne (Phase 5)
        Castle.place("Achteckiges Becken", cx, ML + 0.5, cz, "E");
        // Becken mit dem Rhenus vor der Großen Grotte (nur geplant)
        double bx0 = GROSS_X - 7, bx1 = GROSS_X + 7, bz0 = M[1] - 7.5, bz1 = M[1] - 2.5;
        double y0 = KL;
        mb.box(bx0 - 0.5, y0, bz0 - 0.5, bx1 + 0.5, y0 + 0.6, bz0, Mat.PLANNED, false);
        mb.box(bx0 - 0.5, y0, bz1, bx1 + 0.5, y0 + 0.6, bz1 + 0.5, Mat.PLANNED, false);
        mb.box(bx0 - 0.5, y0, bz0, bx0, y0 + 0.6, bz1, Mat.PLANNED, false);
        mb.box(bx1, y0, bz0, bx1 + 0.5, y0 + 0.6, bz1, Mat.PLANNED, false);
        mb.rectH(bx0, bz0, bx1, bz1, y0 + 0.02, true, Mat.STATUE);
        mb.rectH(bx0, bz0, bx1, bz1, y0 + 0.42, true, Mat.BASIN);
        // Rhenus: liegende Gestalt auf einem Sockel in der Mitte des Beckens
        mb.box(GROSS_X - 2.2, y0 + 0.02, (bz0 + bz1) / 2 - 1.0, GROSS_X + 2.2, y0 + 0.9, (bz0 + bz1) / 2 + 1.0, Mat.STATUE, true);
        mb.ellipsoid(GROSS_X, y0 + 1.35, (bz0 + bz1) / 2, 1.6, 0.45, 0.55, 12, 6, Mat.STATUE);
        mb.ellipsoid(GROSS_X - 1.9, y0 + 1.6, (bz0 + bz1) / 2, 0.32, 0.3, 0.3, 10, 6, Mat.STATUE);
        Castle.place("Rhenusbecken", GROSS_X, y0 + 0.4, (bz0 + bz1) / 2, "E");
    }

    // ------------------------------------------------------------ Pflanzen

    static void tub(MeshBuilder mb, double x, double y, double z) {
        mb.box(x - 0.55, y, z - 0.55, x + 0.55, y + 0.95, z + 0.55, Mat.WOOD, true);
        mb.box(x - 0.6, y + 0.85, z - 0.6, x + 0.6, y + 0.95, z + 0.6, Mat.WOOD, true);
    }

    static void pomeranzen(MeshBuilder mb, Random rnd) {
        List<double[]> at = new ArrayList<>();
        for (double x = -124; x < -14; x += 8) at.add(new double[]{x, ML, 117.4});
        for (double z = 92; z < 116; z += 8) at.add(new double[]{-11.0, ML, z});
        for (double z = 14; z < 60; z += 9) at.add(new double[]{-79.5, KL, z});
        for (double x = -126; x < -90; x += 12) at.add(new double[]{x, KL, 11.5});
        for (double[] p : at) {
            tub(mb, p[0], p[1], p[2]);
            double h = 1.9 + 0.3 * rnd.nextDouble();
            mb.cylinder(p[0], p[2], p[1] + 0.95, p[1] + h, 0.1, 0.07, 6, Mat.WOOD, false);
            mb.ellipsoid(p[0], p[1] + h + 0.65, p[2], 1.15, 0.95, 1.15, 12, 7, Mat.ORANGE);
        }
        Castle.place("Pomeranzenbäume (" + at.size() + " Kästen)", -70, ML, 117.4, "E");
    }

    static void koniferen(MeshBuilder mb, Random rnd) {
        for (double x = -126; x <= -14; x += 8) {
            for (double z : new double[]{67.5, 78.5}) {
                if (Math.abs(x - GROSS_X) < 8 || Math.abs(x - KLEIN_X) < 3) continue;
                double h = 3.2 + 0.8 * rnd.nextDouble();
                mb.cylinder(x, z, KL, KL + 0.9, 0.12, 0.1, 6, Mat.WOOD, false);
                mb.cylinder(x, z, KL + 0.7, KL + 0.7 + h, 1.15, 0.03, 12, Mat.SPRUCE, false);
            }
        }
    }

    static void obereTerrasse(MeshBuilder mb, Random rnd) {
        // Standbilder entlang der oberen Terrasse und eine Hecke dahinter
        for (double x = -124; x < -12; x += 14) Arch.statue(mb, x, UL, 122.4, 2.4, 0, Mat.STATUE);
        ribbon(mb, new double[][]{{-130, 127.2}, {-10, 127.2}}, UL, 1.6, 1.8, Mat.HEDGE);
        // Rasenstreifen davor
        mb.rectH(-130, 123.4, -10, 126.0, UL + 0.025, true, Mat.LAWN);
    }

    // ------------------------------------------------------------ Elisabethentor

    /** Elisabethentor (1615, in einer Nacht für Elisabeth Stuart): rustiziertes Tor mit Säulen und Giebel. */
    static void elisabethentor(MeshBuilder mb) {
        double xc = -104, z = K2[1];
        int v0 = mb.vertexCount();
        double L = 16;
        List<Arch.Op> ops = new ArrayList<>();
        ops.add(Arch.arch(L / 2, KL + 0.05, 5.0, 7.0));
        Arch.wall(mb, L, KL - 4, KL + 9.6, 3.0, ops, Mat.SANDSTONE, Mat.STATUE, false);
        // Torweg durchgehend: Rückwand öffnen geht nicht in der Nische; deshalb durchgehender Gang aus zwei Wangen
        Arch.rib(mb, -0.4, L + 0.4, KL + 9.0, KL + 9.6, 0.7, Mat.STATUE);
        Arch.rib(mb, L / 2 - 4.5, L / 2 + 4.5, KL + 7.6, KL + 9.0, 0.5, Mat.STATUE);
        for (int i = 0; i < 4; i++) Arch.column(mb, L / 2 + (i < 2 ? -1 : 1) * (3.4 + (i % 2) * 1.6), 0.55, KL + 0.05, KL + 7.6, 0.4, Mat.STATUE);
        // Giebel: Dreieck über dem Mittelteil
        mb.quad(new double[]{L / 2 - 5, KL + 9.6, 0.4}, new double[]{L / 2 + 5, KL + 9.6, 0.4}, new double[]{L / 2, KL + 12.2, 0.4}, new double[]{L / 2, KL + 12.2, 0.4}, 0, 0, 1, Mat.SANDSTONE);
        mb.transform(v0, Math.PI, xc + L / 2, 0, z);
        Arch.statue(mb, xc, KL + 9.6, z - 1.0, 2.6, 0, Mat.STATUE);
    }
}
