package com.dan.heidelberg.castle;

import com.dan.heidelberg.core.Mat;
import com.dan.heidelberg.core.MeshBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Fassadengenerator: Wände mit echten Öffnungen (Fenster, Türen, Rundbögen), Laibungen, Gewänden,
 * Sprossenkreuzen und Glas; dazu Dächer (Sattel, Walm), Türme, Mauern, Standbilder. Gebaut wird in
 * einem Wandrahmen (Außenfläche in der Ebene z = 0, u nach rechts, y nach oben) und dann an Ort und
 * Stelle gedreht. Alles parametrisch, alle Maße Meter.
 */
public final class Arch {
    private Arch() { }

    /** Öffnung in einer Wand: kind 0 Fenster mit Glas, 1 offenes Fenster, 2 offene Tür, 3 offener Rundbogen (y1 = Scheitel). */
    public static final class Op {
        public final double u0, u1, y0, y1;
        public final int kind;
        public Op(double u0, double u1, double y0, double y1, int kind) { this.u0 = u0; this.u1 = u1; this.y0 = y0; this.y1 = y1; this.kind = kind; }
        double spring() { return kind == 3 ? y1 - (u1 - u0) / 2 : y1; }
    }

    public static Op win(double uc, double sill, double w, double h, boolean glass) { return new Op(uc - w / 2, uc + w / 2, sill, sill + h, glass ? 0 : 1); }
    public static Op door(double uc, double yb, double w, double h) { return new Op(uc - w / 2, uc + w / 2, yb, yb + h, 2); }
    public static Op arch(double uc, double yb, double w, double h) { return new Op(uc - w / 2, uc + w / 2, yb, yb + h, 3); }

    // ------------------------------------------------------------ Hilfen

    static double[] P(double u, double y, double z) { return new double[]{u, y, z}; }

    /** Viereck mit Normale aus den Ecken; zeigt sie gegen den Hinweis, wird sie umgedreht. */
    static void quadAuto(MeshBuilder mb, int m, double[] a, double[] b, double[] c, double[] d, double hx, double hy, double hz) {
        double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2];
        double vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
        if (Math.abs(vx) + Math.abs(vy) + Math.abs(vz) < 1e-9) { vx = c[0] - a[0]; vy = c[1] - a[1]; vz = c[2] - a[2]; }
        double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
        if (nx * hx + ny * hy + nz * hz < 0) { nx = -nx; ny = -ny; nz = -nz; }
        mb.quad(a, b, c, d, nx, ny, nz, m);
    }

    // ------------------------------------------------------------ Wand

    /**
     * Wand der Länge L von yb bis yt, Dicke T nach innen (z < 0). Außenfläche bei z = 0. Mit hollow
     * auch die Innenfläche und durchgehende Laibungen (begehbarer Raum), sonst Nischen von 0,8 m.
     */
    public static void wall(MeshBuilder mb, double L, double yb, double yt, double T, List<Op> ops, int mat, int frameMat, boolean hollow) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        plane(mb, L, yb, yt, 0, 1, ops, mat);
        if (hollow) plane(mb, L, yb, yt, -T, -1, ops, Mat.PLASTER);
        double depth = hollow ? T : Math.min(T, 0.8);
        for (Op o : ops) {
            double sp = o.spring();
            boolean ground = o.kind >= 2;
            // Laibungen
            mb.quad(P(o.u0, o.y0, 0), P(o.u0, o.y0, -depth), P(o.u0, sp, -depth), P(o.u0, sp, 0), 1, 0, 0, mat);
            mb.quad(P(o.u1, o.y0, 0), P(o.u1, o.y0, -depth), P(o.u1, sp, -depth), P(o.u1, sp, 0), -1, 0, 0, mat);
            if (!ground || o.y0 > yb + 0.01) mb.quad(P(o.u0, o.y0, 0), P(o.u1, o.y0, 0), P(o.u1, o.y0, -depth), P(o.u0, o.y0, -depth), 0, 1, 0, mat);
            if (o.kind == 3) {
                final double uc = (o.u0 + o.u1) / 2, r = (o.u1 - o.u0) / 2;
                mb.patch((th, z, p, n) -> {
                    p[0] = uc + r * Math.cos(th); p[1] = sp + r * Math.sin(th); p[2] = z;
                    n[0] = -Math.cos(th); n[1] = -Math.sin(th); n[2] = 0;
                }, 0, Math.PI, 14, -depth, 0, 1, mat);
                capStrips(mb, o, 0, 1, mat);
                if (hollow) capStrips(mb, o, -T, -1, Mat.PLASTER);
            } else {
                mb.quad(P(o.u0, o.y1, 0), P(o.u1, o.y1, 0), P(o.u1, o.y1, -depth), P(o.u0, o.y1, -depth), 0, -1, 0, mat);
            }
            if (o.kind == 0) {
                // Glas in der Mitte der Laibung, Sprossenkreuz aus Stein davor
                double gz = -Math.min(depth * 0.5, 0.35);
                mb.quad(P(o.u0, o.y0, gz), P(o.u1, o.y0, gz), P(o.u1, o.y1, gz), P(o.u0, o.y1, gz), 0, 0, 1, Mat.GLASS);
                double uc = (o.u0 + o.u1) / 2, h = o.y1 - o.y0;
                mb.box(uc - 0.05, o.y0, gz - 0.04, uc + 0.05, o.y1, gz + 0.04, frameMat, false);
                mb.box(o.u0, o.y0 + h * 0.62 - 0.05, gz - 0.04, o.u1, o.y0 + h * 0.62 + 0.05, gz + 0.04, frameMat, false);
            } else if (!hollow) {
                // blinder Grund (Raum dahinter nicht gebaut)
                if (o.kind == 3) mb.quad(P(o.u0, o.y0, -depth), P(o.u1, o.y0, -depth), P(o.u1, o.y1, -depth), P(o.u0, o.y1, -depth), 0, 0, 1, Mat.PLASTER);
                else mb.quad(P(o.u0, o.y0, -depth), P(o.u1, o.y0, -depth), P(o.u1, o.y1, -depth), P(o.u0, o.y1, -depth), 0, 0, 1, Mat.PLASTER);
            }
            // Gewände: Rahmen aus Stein, leicht vorspringend
            if (o.kind <= 1) {
                double f = 0.22;
                mb.box(o.u0 - f, o.y0 - 0.14, 0, o.u1 + f, o.y0, 0.14, frameMat, false);              // Sohlbank
                mb.box(o.u0 - f, o.y0, 0, o.u0, o.y1 + f, 0.07, frameMat, false);
                mb.box(o.u1, o.y0, 0, o.u1 + f, o.y1 + f, 0.07, frameMat, false);
                mb.box(o.u0 - f, o.y1, 0, o.u1 + f, o.y1 + f, 0.1, frameMat, false);
            } else if (o.kind == 2) {
                double f = 0.25;
                mb.box(o.u0 - f, o.y0, 0, o.u0, o.y1 + f, 0.1, frameMat, false);
                mb.box(o.u1, o.y0, 0, o.u1 + f, o.y1 + f, 0.1, frameMat, false);
                mb.box(o.u0 - f, o.y1, 0, o.u1 + f, o.y1 + f, 0.14, frameMat, false);
            }
        }
        mb.maxEdge = keep;
    }

    /** Die Zwickel über einem Rundbogen: Streifen zwischen Bogenlinie und Scheitelhöhe. */
    static void capStrips(MeshBuilder mb, Op o, double z, double nz, int mat) {
        double uc = (o.u0 + o.u1) / 2, r = (o.u1 - o.u0) / 2, sp = o.spring();
        int n = 14;
        for (int i = 0; i < n; i++) {
            double ua = o.u0 + (o.u1 - o.u0) * i / n, ub = o.u0 + (o.u1 - o.u0) * (i + 1) / n;
            double ya = sp + Math.sqrt(Math.max(0, r * r - (ua - uc) * (ua - uc))), yb2 = sp + Math.sqrt(Math.max(0, r * r - (ub - uc) * (ub - uc)));
            mb.quad(P(ua, ya, z), P(ub, yb2, z), P(ub, o.y1, z), P(ua, o.y1, z), 0, 0, nz, mat);
        }
    }

    private static void plane(MeshBuilder mb, double L, double yb, double yt, double z, double nz, List<Op> ops, int mat) {
        TreeSet<Double> us = new TreeSet<>(), ys = new TreeSet<>();
        us.add(0.0); us.add(L); ys.add(yb); ys.add(yt);
        for (Op o : ops) {
            if (o.u0 > 0 && o.u0 < L) us.add(o.u0);
            if (o.u1 > 0 && o.u1 < L) us.add(o.u1);
            if (o.y0 > yb && o.y0 < yt) ys.add(o.y0);
            if (o.y1 > yb && o.y1 < yt) ys.add(o.y1);
        }
        Double[] ua = us.toArray(new Double[0]), ya = ys.toArray(new Double[0]);
        for (int j = 0; j + 1 < ya.length; j++) {
            for (int i = 0; i + 1 < ua.length; i++) {
                double cu = (ua[i] + ua[i + 1]) / 2, cy = (ya[j] + ya[j + 1]) / 2;
                boolean in = false;
                for (Op o : ops) if (cu > o.u0 && cu < o.u1 && cy > o.y0 && cy < o.y1) { in = true; break; }
                if (in) continue;
                mb.quad(P(ua[i], ya[j], z), P(ua[i + 1], ya[j], z), P(ua[i + 1], ya[j + 1], z), P(ua[i], ya[j + 1], z), 0, 0, nz, mat);
            }
        }
    }

    /** Vorspringender Streifen auf der Außenfläche (Gesims, Lisene) im Wandrahmen. */
    public static void rib(MeshBuilder mb, double u0, double u1, double y0, double y1, double depth, int mat) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.box(u0, y0, 0, u1, y1, depth, mat, false);
        mb.maxEdge = keep;
    }

    // ------------------------------------------------------------ Baukörper

    public static final class Face { public final List<Op> ops = new ArrayList<>(); public double[][] ribs = new double[0][]; }

    /** Quaderförmiger Baukörper, Wandseiten 0 = +z, 1 = +x, 2 = −z, 3 = −x (im Körperrahmen); yaw dreht ihn. */
    public static final class Block {
        public double cx, cz, yaw, W, D, y0, y1;
        public int mat = Mat.SANDSTONE, frame = Mat.STATUE;
        public boolean hollow;
        public double T = 1.3;
        public final Face[] f = {new Face(), new Face(), new Face(), new Face()};
        /** Böden im Raum: {y, Material}. */
        public final List<double[]> slabs = new ArrayList<>();
        /** Sockel (Meter über y0 hinaus nach unten, damit nichts schwebt). */
        public double foot = 3;

        public Block(double cx, double cz, double yaw, double W, double D, double y0, double y1) {
            this.cx = cx; this.cz = cz; this.yaw = yaw; this.W = W; this.D = D; this.y0 = y0; this.y1 = y1;
        }

        /** Fenster in gleichen Abständen: n Achsen auf der Wandseite k, Geschosse bei sill[] mit Höhe h, Breite w. */
        public Block windows(int k, int n, double margin, double[] sills, double w, double h, boolean glass) {
            double L = (k % 2 == 0) ? W : D;
            double span = L - 2 * margin;
            for (double s : sills) for (int i = 0; i < n; i++) f[k].ops.add(win(margin + span * (i + 0.5) / n, y0 + s, w, h, glass));
            return this;
        }

        /** Baut den Körper (Wände); das Dach kommt getrennt über {@link #roof}. Liefert den Anfangsindex der Ecken. */
        public int build(MeshBuilder mb) {
            if (Ruin.active) return Ruin.block(mb, this);
            int v0 = mb.vertexCount();
            double yb = y0 - foot;
            double[][] start = {{-W / 2, D / 2}, {W / 2, D / 2}, {W / 2, -D / 2}, {-W / 2, -D / 2}};
            MeshBuilder.SkyFn keepFn = mb.skyFn;
            for (int k = 0; k < 4; k++) {
                int vf = mb.vertexCount();
                // Innenseite der Wand (z < −0,25 im Wandrahmen) bekommt weniger Himmelslicht
                if (hollow) mb.skyFn = (x, y, z, nx, ny, nz) -> z < -0.25 ? 0.22f : 1f;
                wall(mb, len(k), yb, y1, T, f[k].ops, mat, frame, hollow);
                mb.skyFn = keepFn;
                for (double[] r : f[k].ribs) rib(mb, r[0], r[1], r[2], r[3], r[4], r.length > 5 ? (int) r[5] : frame);
                mb.transform(vf, k * Math.PI / 2, start[k][0], 0, start[k][1]);
            }
            if (hollow) {
                mb.skyFn = (x, y, z, nx, ny, nz) -> 0.22f;
                for (double[] sl : slabs) mb.rectH(-W / 2 + T, -D / 2 + T, W / 2 - T, D / 2 - T, sl[0], true, (int) sl[1]);
                mb.rectH(-W / 2 + T, -D / 2 + T, W / 2 - T, D / 2 - T, y1 - 0.2, false, Mat.PLASTER);
                mb.skyFn = keepFn;
            }
            mb.transform(v0, yaw, cx, 0, cz);
            return v0;
        }

        /** Dach: Sattel (hip = false) oder Walm; Traufe auf y1, Höhe rise, Überstand over; Firstrichtung entlang W. */
        public void roof(MeshBuilder mb, double rise, double over, boolean hip, int roofMat, int gableMat) {
            if (Ruin.active && !Ruin.keepRoof(this)) return;
            int v0 = mb.vertexCount();
            gableRoof(mb, W, D, y1, rise, over, hip, roofMat, gableMat);
            mb.transform(v0, yaw, cx, 0, cz);
            if (!Ruin.active) {
                // Firstlinie für den Brand (Phase 9): Enden in Weltkoordinaten
                double xr = hip ? Math.max(0, W / 2 - D / 2) : W / 2 + over;
                double[] a = world(-xr, 0), c = world(xr, 0);
                Castle.RIDGES.add(new double[]{a[0], y1 + rise, a[1], c[0], y1 + rise, c[1], rise});
            }
        }

        /** Baut im Körperrahmen (x längs W, z längs D, y absolut) und stellt das Ergebnis an Ort und Stelle. */
        public void local(MeshBuilder mb, Runnable r) {
            int v0 = mb.vertexCount();
            r.run();
            mb.transform(v0, yaw, cx, 0, cz);
        }

        /**
         * Baut im Wandrahmen der Seite k (Außenfläche z = 0, u von links nach rechts, von der Ecke u0 aus,
         * y absolut) und stellt das Ergebnis an Ort und Stelle.
         */
        public void onFace(MeshBuilder mb, int k, double u0, Runnable r) {
            double[][] start = {{-W / 2, D / 2}, {W / 2, D / 2}, {W / 2, -D / 2}, {-W / 2, -D / 2}};
            double[][] dir = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};
            int v0 = mb.vertexCount();
            r.run();
            mb.transform(v0, k * Math.PI / 2, start[k][0] + dir[k][0] * u0, 0, start[k][1] + dir[k][1] * u0);
            mb.transform(v0, yaw, cx, 0, cz);
        }

        /**
         * Weltlage eines Punktes auf der Wandseite k: u längs der Wand, out Meter vor der Wand; liefert x, z und die Blickrichtung
         * nach außen (nx, nz).
         */
        public double[] faceWorld(int k, double u, double out) {
            double[][] start = {{-W / 2, D / 2}, {W / 2, D / 2}, {W / 2, -D / 2}, {-W / 2, -D / 2}};
            double[][] dir = {{1, 0}, {0, -1}, {-1, 0}, {0, 1}};
            double[][] nrm = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
            double bu = start[k][0] + dir[k][0] * u + nrm[k][0] * out, bv = start[k][1] + dir[k][1] * u + nrm[k][1] * out;
            double[] p = world(bu, bv);
            double c = Math.cos(yaw), s = Math.sin(yaw);
            return new double[]{p[0], p[1], nrm[k][0] * c + nrm[k][1] * s, -nrm[k][0] * s + nrm[k][1] * c};
        }

        /** Länge der Wandseite k. */
        public double len(int k) { return (k % 2 == 0) ? W : D; }

        /** Mittelpunkt und Drehung in Weltkoordinaten: Weltpunkt zu Körperpunkt (u längs W, v längs D). */
        public double[] world(double u, double v) {
            double c = Math.cos(yaw), s = Math.sin(yaw);
            return new double[]{cx + u * c + v * s, cz - u * s + v * c};
        }
    }

    /** Satteldach oder Walmdach im Körperrahmen: Firstrichtung entlang x, Mitte im Ursprung. */
    public static void gableRoof(MeshBuilder mb, double W, double D, double yE, double rise, double over, boolean hip, int roofMat, int gableMat) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double ze = D / 2 + over, xe = W / 2 + over;
        double ye = yE - over * rise / (D / 2);
        double yr = yE + rise;
        double xr = hip ? Math.max(0, W / 2 - D / 2) : xe;
        quadAuto(mb, roofMat, P(-xe, ye, ze), P(xe, ye, ze), P(xr, yr, 0), P(-xr, yr, 0), 0, 1, 1);
        quadAuto(mb, roofMat, P(-xe, ye, -ze), P(xe, ye, -ze), P(xr, yr, 0), P(-xr, yr, 0), 0, 1, -1);
        if (hip) {
            quadAuto(mb, roofMat, P(xe, ye, -ze), P(xe, ye, ze), P(xr, yr, 0), P(xr, yr, 0), 1, 1, 0);
            quadAuto(mb, roofMat, P(-xe, ye, -ze), P(-xe, ye, ze), P(-xr, yr, 0), P(-xr, yr, 0), -1, 1, 0);
        } else {
            double xw = W / 2;
            double yg = yE + rise * (D / 2) / (D / 2 + over) * 1.0;
            quadAuto(mb, gableMat, P(xw, yE, -D / 2), P(xw, yE, D / 2), P(xw, yE + rise * (D / 2) / ze, 0), P(xw, yE + rise * (D / 2) / ze, 0), 1, 0, 0);
            quadAuto(mb, gableMat, P(-xw, yE, -D / 2), P(-xw, yE, D / 2), P(-xw, yE + rise * (D / 2) / ze, 0), P(-xw, yE + rise * (D / 2) / ze, 0), -1, 0, 0);
        }
        mb.maxEdge = keep;
    }

    // ------------------------------------------------------------ Türme, Mauern, Figuren

    /** Runder Turm mit Kranzgesims und Kegeldach (Helm). */
    public static void tower(MeshBuilder mb, double cx, double cz, double r, double yb, double yt, double helm, int mat, int roofMat, int slits) {
        double keep = mb.maxEdge;
        mb.maxEdge = 6;
        mb.cylinder(cx, cz, yb, yt, r * 1.06, r, 28, mat, false);
        // Wulst und Kranz
        mb.cylinder(cx, cz, yt - 1.4, yt - 0.7, r * 1.0, r * 1.18, 28, mat, false);
        mb.cylinder(cx, cz, yt - 0.7, yt, r * 1.18, r * 1.18, 28, mat, false);
        mb.cylinder(cx, cz, yt, yt + 0.1, r * 1.18, r * 1.18, 28, mat, true);
        if (helm > 0) mb.cylinder(cx, cz, yt, yt + helm, r * 1.12, 0.05, 28, roofMat, false);
        mb.maxEdge = 1e9;
        // Schießscharten als dunkle Nischen
        for (int i = 0; i < slits; i++) {
            double a = i * 2 * Math.PI / slits + 0.3, h = yb + (yt - yb) * (0.35 + 0.35 * ((i * 7) % 5) / 5.0);
            double ox = cx + Math.cos(a) * (r + 0.02), oz = cz + Math.sin(a) * (r + 0.02);
            double tx = -Math.sin(a), tz = Math.cos(a);
            mb.quad(new double[]{ox - tx * 0.12, h, oz - tz * 0.12}, new double[]{ox + tx * 0.12, h, oz + tz * 0.12},
                    new double[]{ox + tx * 0.12, h + 1.2, oz + tz * 0.12}, new double[]{ox - tx * 0.12, h + 1.2, oz - tz * 0.12},
                    Math.cos(a), 0, Math.sin(a), Mat.PLASTER);
        }
        mb.maxEdge = keep;
        if (!Ruin.active && helm > 0) Castle.TOPS.add(new double[]{cx, yt + helm * 0.4, cz, r});
    }

    /** Gerade Mauer von (x0,z0) nach (x1,z1): Höhe bis yt, Dicke t, mit Brüstung und Zinnen. */
    public static void curtain(MeshBuilder mb, double x0, double z0, double x1, double z1, double yb, double yt, double t, int mat, boolean merlons) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz);
        double yaw = Math.atan2(dx, dz);
        // Kasten um die Mitte; Länge entlang v
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int v0 = mb.vertexCount();
        // im Rahmen: Länge entlang z, Breite entlang x
        mb.box(-t / 2, yb, -l / 2, t / 2, yt, l / 2, mat, false);
        if (merlons) {
            int n = (int) Math.floor(l / 2.4);
            for (int i = 0; i < n; i++) {
                double zc = -l / 2 + (i + 0.5) * l / n;
                mb.box(-t / 2 - 0.15, yt, zc - 0.55, t / 2 + 0.15, yt + 1.3, zc + 0.55, mat, true);
            }
            mb.box(-t / 2 - 0.15, yt - 0.35, -l / 2, t / 2 + 0.15, yt, l / 2, mat, false);
        }
        mb.transform(v0, yaw, cx, 0, cz);
        mb.maxEdge = keep;
    }

    /** Standbild von h Metern auf Sockel, blickt in Richtung (fx, fz). */
    public static void statue(MeshBuilder mb, double x, double y, double z, double h, double yaw, int mat) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        int v0 = mb.vertexCount();
        double s = h / 2.0;
        mb.box(-0.32 * s, 0, -0.32 * s, 0.32 * s, 0.35 * s, 0.32 * s, mat, true);
        mb.cylinder(0, 0, 0.35 * s, 1.35 * s, 0.30 * s, 0.17 * s, 10, mat, true);
        mb.ellipsoid(0, 1.55 * s, 0, 0.30 * s, 0.14 * s, 0.14 * s, 8, 5, mat);
        mb.ellipsoid(0, 1.80 * s, 0, 0.115 * s, 0.14 * s, 0.12 * s, 8, 5, mat);
        mb.transform(v0, yaw, x, y, z);
        mb.maxEdge = keep;
    }

    /** Kreisscheibe (Wasserfläche, Boden) bei Höhe y, Normale nach oben. */
    public static void disc(MeshBuilder mb, double cx, double cz, double y, double r, int seg, int mat) {
        mb.patch((u, v, p, n) -> {
            p[0] = cx + v * Math.cos(u); p[1] = y; p[2] = cz + v * Math.sin(u);
            n[0] = 0; n[1] = 1; n[2] = 0;
        }, 0, 2 * Math.PI, seg, 0, r, Math.max(1, (int) Math.ceil(r / Math.max(0.5, mb.maxEdge))), mat);
    }

    /** Ring (Beckenrand oben) von r0 bis r1 bei Höhe y. */
    public static void ring(MeshBuilder mb, double cx, double cz, double y, double r0, double r1, int seg, int mat) {
        mb.patch((u, v, p, n) -> {
            p[0] = cx + v * Math.cos(u); p[1] = y; p[2] = cz + v * Math.sin(u);
            n[0] = 0; n[1] = 1; n[2] = 0;
        }, 0, 2 * Math.PI, seg, r0, r1, 1, mat);
    }

    /** Rundes Becken: Wand von yb bis yRim (Außenradius r + w), Rand oben, Wasser auf yWater, Grund. */
    public static void roundBasin(MeshBuilder mb, double cx, double cz, double yb, double yRim, double yWater, double r, double w, int seg, int rimMat, int waterMat) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.cylinder(cx, cz, yb, yRim, r + w, r + w, seg, rimMat, false);
        ring(mb, cx, cz, yRim, r, r + w, seg, rimMat);
        mb.cylinder(cx, cz, yWater, yRim, r, r, seg, rimMat, false);
        disc(mb, cx, cz, yWater, r, seg, waterMat);
        disc(mb, cx, cz, yWater - 0.7, r, seg, Mat.STATUE);
        mb.maxEdge = keep;
    }

    /** Säule mit Basis und Kapitell, senkrecht. */
    public static void column(MeshBuilder mb, double x, double z, double yb, double yt, double r, int mat) {
        double keep = mb.maxEdge;
        mb.maxEdge = 1e9;
        mb.box(x - r * 1.5, yb, z - r * 1.5, x + r * 1.5, yb + r * 1.0, z + r * 1.5, mat, true);
        mb.cylinder(x, z, yb + r, yt - r * 1.1, r, r * 0.84, 14, mat, false);
        mb.box(x - r * 1.6, yt - r * 1.1, z - r * 1.6, x + r * 1.6, yt, z + r * 1.6, mat, true);
        mb.maxEdge = keep;
    }
}
