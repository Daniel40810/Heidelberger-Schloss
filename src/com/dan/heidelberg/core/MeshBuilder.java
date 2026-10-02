package com.dan.heidelberg.core;

import java.util.Arrays;

/** Sammelt Ecken und Dreiecke und baut daraus ein {@link Mesh}. Übernommen aus Semiramis. */
public final class MeshBuilder {
    private float[] pos = new float[3 * 8192], nrm = new float[3 * 8192], sky = new float[8192], sway = new float[8192], flutter = new float[8192];
    private int[] spin = new int[8192];
    private final java.util.List<double[]> spinners = new java.util.ArrayList<>();

    /** Ausschlag im Wind für alle folgenden Ecken (Meter); swayFn hat Vorrang, wenn gesetzt. */
    public double swayValue;
    public interface SwayFn { double sway(double x, double y, double z); }
    public SwayFn swayFn;
    /** Zittern der Blätter für alle folgenden Ecken (Meter, entlang der Normale). */
    public double flutterValue;
    /** Drehkörper für alle folgenden Ecken, −1 = keiner. */
    public int spinId = -1;

    /** Neue Drehachse durch (ox,oy,oz) in Richtung (dx,dy,dz), omega in rad/s; liefert die Nummer. */
    public int addSpinner(double ox, double oy, double oz, double dx, double dy, double dz, double omega) {
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        spinners.add(new double[]{ox, oy, oz, dx / l, dy / l, dz / l, omega});
        return spinners.size() - 1;
    }
    private int nv;
    private int[] idx = new int[3 * 8192];
    private byte[] mat = new byte[8192], grp = new byte[8192], ear = new byte[8192];
    private int nt;

    /** Größte Kantenlänge beim Unterteilen von Flächen (Meter). */
    public double maxEdge = 2.5;
    /** Himmelssicht 0..1 je Ecke (Verschattung in Ecken, Gewölben, am Fuß); null = frei. */
    public interface SkyFn { float sky(double x, double y, double z, double nx, double ny, double nz); }

    /** Gilt für alle folgenden Ecken; null = 1. */
    public SkyFn skyFn;

    /** Gruppe für alle folgenden Dreiecke. */
    public int group = 0;

    /** Zeitstufen (Phase 9): Schloss 1619, Ruine nach 1689, nach 1693, nach 1764; ALL gilt in jeder Stufe (Gelände, Wald, Stadt, Garten). */
    public static final int ERA_1619 = 1, ERA_1689 = 2, ERA_1693 = 4, ERA_1764 = 8, ERA_ALL = 15;
    /** Zeitstufen der folgenden Dreiecke (Bitmaske). */
    public int era = ERA_ALL;

    public int vertexCount() { return nv; }
    public int triCount() { return nt; }

    public int v(double x, double y, double z, double nx, double ny, double nz) {
        if (3 * nv + 3 > pos.length) {
            pos = Arrays.copyOf(pos, pos.length * 2);
            nrm = Arrays.copyOf(nrm, nrm.length * 2);
            sky = Arrays.copyOf(sky, sky.length * 2);
            sway = Arrays.copyOf(sway, sway.length * 2);
            flutter = Arrays.copyOf(flutter, flutter.length * 2);
            spin = Arrays.copyOf(spin, spin.length * 2);
        }
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-12) { nx = 0; ny = 1; nz = 0; l = 1; }
        int o = 3 * nv;
        pos[o] = (float) x; pos[o + 1] = (float) y; pos[o + 2] = (float) z;
        nrm[o] = (float) (nx / l); nrm[o + 1] = (float) (ny / l); nrm[o + 2] = (float) (nz / l);
        sky[nv] = skyFn == null ? 1f : Math.max(0f, Math.min(1f, skyFn.sky(x, y, z, nx / l, ny / l, nz / l)));
        sway[nv] = (float) (swayFn != null ? swayFn.sway(x, y, z) : swayValue);
        flutter[nv] = (float) flutterValue;
        spin[nv] = spinId;
        return nv++;
    }

    public void tri(int a, int b, int c, int m) {
        if (a == b || b == c || a == c) return;
        if (3 * nt + 3 > idx.length) {
            idx = Arrays.copyOf(idx, idx.length * 2);
            mat = Arrays.copyOf(mat, mat.length * 2);
            grp = Arrays.copyOf(grp, grp.length * 2);
            ear = Arrays.copyOf(ear, ear.length * 2);
        }
        int o = 3 * nt;
        idx[o] = a; idx[o + 1] = b; idx[o + 2] = c;
        mat[nt] = (byte) m; grp[nt] = (byte) group; ear[nt] = (byte) era;
        nt++;
    }

    /** Himmelssicht der Ecken v0 bis v1 (ohne v1) mit k malnehmen (Innenräume: dunkleres Umgebungslicht). */
    public void skyMul(int v0, int v1, float k) {
        for (int i = v0; i < v1 && i < nv; i++) sky[i] *= k;
    }

    public int segs(double len) {
        return Math.max(1, (int) Math.ceil(len / maxEdge - 1e-9));
    }

    /** Gitterfläche über [u0,u1]×[v0,v1] mit nu×nv Zellen. */
    /** Fläche in Parameterform: Punkt p und Normale n zu (u, v). */
    public interface SurfFn { void eval(double u, double v, double[] p, double[] n); }

    public void patch(SurfFn f, double u0, double u1, int nu, double v0, double v1, int nvv, int m) {
        int[] g = new int[(nu + 1) * (nvv + 1)];
        double[] p = new double[3], n = new double[3];
        for (int j = 0; j <= nvv; j++) {
            double v = v0 + (v1 - v0) * j / nvv;
            for (int i = 0; i <= nu; i++) {
                double u = u0 + (u1 - u0) * i / nu;
                f.eval(u, v, p, n);
                g[j * (nu + 1) + i] = v(p[0], p[1], p[2], n[0], n[1], n[2]);
            }
        }
        for (int j = 0; j < nvv; j++) {
            for (int i = 0; i < nu; i++) {
                int a = g[j * (nu + 1) + i], b = a + 0, c, d;
                b = g[j * (nu + 1) + i + 1];
                c = g[(j + 1) * (nu + 1) + i + 1];
                d = g[(j + 1) * (nu + 1) + i];
                tri(a, b, c, m);
                tri(a, c, d, m);
            }
        }
    }

    private static double dist(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Viereck (bilinear) mit fester Normale, nach maxEdge unterteilt. */
    public void quad(double[] p00, double[] p10, double[] p11, double[] p01,
                     double nx, double ny, double nz, int m) {
        double lu = Math.max(dist(p00, p10), dist(p01, p11));
        double lv = Math.max(dist(p00, p01), dist(p10, p11));
        patch((u, v, p, n) -> {
            for (int k = 0; k < 3; k++) {
                p[k] = (1 - u) * (1 - v) * p00[k] + u * (1 - v) * p10[k] + u * v * p11[k] + (1 - u) * v * p01[k];
            }
            n[0] = nx; n[1] = ny; n[2] = nz;
        }, 0, 1, segs(lu), 0, 1, segs(lv), m);
    }

    /** Waagrechtes Rechteck; up=true zeigt nach oben. */
    public void rectH(double x0, double z0, double x1, double z1, double y, boolean up, int m) {
        quad(new double[]{x0, y, z0}, new double[]{x1, y, z0}, new double[]{x1, y, z1}, new double[]{x0, y, z1},
                0, up ? 1 : -1, 0, m);
    }

    public void box(double x0, double y0, double z0, double x1, double y1, double z1, int m, boolean bottom) {
        rectH(x0, z0, x1, z1, y1, true, m);
        if (bottom) rectH(x0, z0, x1, z1, y0, false, m);
        quad(new double[]{x0, y0, z0}, new double[]{x1, y0, z0}, new double[]{x1, y1, z0}, new double[]{x0, y1, z0}, 0, 0, -1, m);
        quad(new double[]{x0, y0, z1}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x0, y1, z1}, 0, 0, 1, m);
        quad(new double[]{x0, y0, z0}, new double[]{x0, y0, z1}, new double[]{x0, y1, z1}, new double[]{x0, y1, z0}, -1, 0, 0, m);
        quad(new double[]{x1, y0, z0}, new double[]{x1, y0, z1}, new double[]{x1, y1, z1}, new double[]{x1, y1, z0}, 1, 0, 0, m);
    }

    /** Kegelstumpf/Zylinder um die senkrechte Achse (cx,cz). */
    public void cylinder(double cx, double cz, double yA, double yB, double rA, double rB, int seg, int m, boolean capTop) {
        double h = yB - yA;
        double slope = (rA - rB) / h;
        int rings = Math.max(1, (int) Math.ceil(h / maxEdge));
        patch((u, v, p, n) -> {
            double r = rA + (rB - rA) * v, c = Math.cos(u), s = Math.sin(u);
            p[0] = cx + r * c; p[1] = yA + h * v; p[2] = cz + r * s;
            n[0] = c; n[1] = slope; n[2] = s;
        }, 0, 2 * Math.PI, seg, 0, 1, rings, m);
        if (capTop) {
            patch((u, v, p, n) -> {
                p[0] = cx + v * Math.cos(u); p[1] = yB; p[2] = cz + v * Math.sin(u);
                n[0] = 0; n[1] = 1; n[2] = 0;
            }, 0, 2 * Math.PI, seg, 0, rB, 1, m);
        }
    }

    public void ellipsoid(double cx, double cy, double cz, double rx, double ry, double rz, int nu, int nvv, int m) {
        patch((u, v, p, n) -> {
            double cv = Math.cos(v), sv = Math.sin(v), cu = Math.cos(u), su = Math.sin(u);
            p[0] = cx + rx * cv * cu; p[1] = cy + ry * sv; p[2] = cz + rz * cv * su;
            n[0] = cv * cu / rx; n[1] = sv / ry; n[2] = cv * su / rz;
        }, 0, 2 * Math.PI, nu, -Math.PI / 2, Math.PI / 2, nvv, m);
    }

    /** Dreht alle Ecken ab v0 um die senkrechte Achse (yaw: +z zeigt danach nach (sin, cos)) und verschiebt sie. */
    public void transform(int v0, double yaw, double tx, double ty, double tz) {
        double c = Math.cos(yaw), s = Math.sin(yaw);
        for (int i = v0; i < nv; i++) {
            int o = 3 * i;
            double x = pos[o], z = pos[o + 2];
            pos[o] = (float) (x * c + z * s + tx); pos[o + 1] += (float) ty; pos[o + 2] = (float) (-x * s + z * c + tz);
            double nx = nrm[o], nz = nrm[o + 2];
            nrm[o] = (float) (nx * c + nz * s); nrm[o + 2] = (float) (-nx * s + nz * c);
        }
    }

    /** Spiegelt alle seit (v0,t0) erzeugten Ecken und Dreiecke an der Ebene x=0 und hängt sie an. */
    public void mirrorX(int v0, int t0) {
        int nv1 = nv, nt1 = nt;
        int off = nv1 - v0;
        for (int i = v0; i < nv1; i++) {
            int o = 3 * i;
            float sk = sky[i];
            v(-pos[o], pos[o + 1], pos[o + 2], -nrm[o], nrm[o + 1], nrm[o + 2]);
            sky[nv - 1] = sk;
        }
        int keep = group, keepEra = era;
        for (int t = t0; t < nt1; t++) {
            group = grp[t];
            era = ear[t];
            tri(idx[3 * t] + off, idx[3 * t + 2] + off, idx[3 * t + 1] + off, mat[t]);
        }
        group = keep;
        era = keepEra;
    }

    /**
     * Nahbereich für die Blockgröße: innerhalb (x0..x1, z0..z1) Blöcke der Kantenlänge cell,
     * außerhalb grobe Blöcke von FAR_CELL Metern (das Gelände reicht 60 km weit).
     */
    public double nearX0 = -1800, nearX1 = 700, nearZ0 = -2200, nearZ1 = 700;
    static final double FAR_CELL = 480;

    /** Baut das Netz; Dreiecke werden in Blöcke der Kantenlänge cell einsortiert. */
    public Mesh build(double cell) {
        return build(cell, ERA_ALL, nv, null);
    }

    /**
     * Baut das Netz einer Zeitstufe: nur Dreiecke, deren Stufen die Maske berühren, und nur die ersten nvLimit Ecken.
     * Mit share teilen sich die Netze Ecken, Normalen und Bewegungsdaten (Wald, Wind), nur die Dreiecke sind eigen.
     */
    public Mesh build(double cell, int eraMask, int nvLimit, Mesh share) {
        int[] sel = new int[nt];
        int ns = 0;
        for (int t = 0; t < nt; t++) {
            if ((ear[t] & eraMask) == 0) continue;
            sel[ns++] = t;
        }
        long[] key = new long[ns];
        for (int q = 0; q < ns; q++) {
            int t = sel[q];
            int a = 3 * idx[3 * t], b = 3 * idx[3 * t + 1], c = 3 * idx[3 * t + 2];
            double cx = (pos[a] + pos[b] + pos[c]) / 3, cz = (pos[a + 2] + pos[b + 2] + pos[c + 2]) / 3;
            boolean far = cx < nearX0 || cx > nearX1 || cz < nearZ0 || cz > nearZ1;
            double cs0 = far ? FAR_CELL : cell;
            long ix = Math.max(0, Math.min(1023, (long) Math.floor(cx / cs0) + 512));
            long iz = Math.max(0, Math.min(1023, (long) Math.floor(cz / cs0) + 512));
            long k = ((far ? 1L : 0L) << 22) | (ix << 12) | (iz << 2) | (grp[t] & 3);
            key[q] = (k << 22) | t;  // k < 2^23, t < 2^22
        }
        Arrays.sort(key);
        int[] nIdx = new int[3 * ns];
        byte[] nMat = new byte[ns], nGrp = new byte[ns];
        float[] fn = new float[3 * ns];
        int[] cs = new int[ns + 1], cc = new int[ns + 1], cg = new int[ns + 1];
        int nc = 0;
        long prev = -1;
        for (int i = 0; i < ns; i++) {
            int t = (int) (key[i] & ((1L << 22) - 1));
            long k = key[i] >>> 22;
            if (k != prev) { cs[nc] = i; cg[nc] = (int) (k & 3); nc++; prev = k; }
            cc[nc - 1]++;
            nIdx[3 * i] = idx[3 * t]; nIdx[3 * i + 1] = idx[3 * t + 1]; nIdx[3 * i + 2] = idx[3 * t + 2];
            nMat[i] = mat[t]; nGrp[i] = grp[t];
            double sx = 0, sy = 0, sz = 0;
            for (int k2 = 0; k2 < 3; k2++) {
                int o = 3 * idx[3 * t + k2];
                sx += nrm[o]; sy += nrm[o + 1]; sz += nrm[o + 2];
            }
            double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
            if (l < 1e-9) { sx = 0; sy = 1; sz = 0; l = 1; }
            fn[3 * i] = (float) (sx / l); fn[3 * i + 1] = (float) (sy / l); fn[3 * i + 2] = (float) (sz / l);
        }
        float[] cb = new float[6 * nc];
        for (int c = 0; c < nc; c++) {
            float mnx = Float.MAX_VALUE, mny = mnx, mnz = mnx, mxx = -mnx, mxy = -mnx, mxz = -mnx;
            for (int i = cs[c]; i < cs[c] + cc[c]; i++) {
                for (int k2 = 0; k2 < 3; k2++) {
                    int o = 3 * nIdx[3 * i + k2];
                    if (o >= 3 * nvLimit) throw new IllegalStateException("Dreieck nutzt Ecke jenseits der Grenze");
                    mnx = Math.min(mnx, pos[o]); mny = Math.min(mny, pos[o + 1]); mnz = Math.min(mnz, pos[o + 2]);
                    mxx = Math.max(mxx, pos[o]); mxy = Math.max(mxy, pos[o + 1]); mxz = Math.max(mxz, pos[o + 2]);
                }
            }
            cb[6 * c] = mnx; cb[6 * c + 1] = mny; cb[6 * c + 2] = mnz;
            cb[6 * c + 3] = mxx; cb[6 * c + 4] = mxy; cb[6 * c + 5] = mxz;
        }
        Mesh mesh;
        if (share == null) {
            mesh = new Mesh(nvLimit, ns, Arrays.copyOf(pos, 3 * nv), Arrays.copyOf(nrm, 3 * nv), nIdx, nMat, nGrp, fn,
                    nc, Arrays.copyOf(cs, nc), Arrays.copyOf(cc, nc), Arrays.copyOf(cg, nc), cb);
            System.arraycopy(sky, 0, mesh.sky, 0, nv);
            System.arraycopy(sway, 0, mesh.sway, 0, nv);
            System.arraycopy(flutter, 0, mesh.flutter, 0, nv);
            System.arraycopy(spin, 0, mesh.spin, 0, nv);
            mesh.spinners = spinners.toArray(new double[0][]);
        } else {
            mesh = new Mesh(nvLimit, ns, share, nIdx, nMat, nGrp, fn,
                    nc, Arrays.copyOf(cs, nc), Arrays.copyOf(cc, nc), Arrays.copyOf(cg, nc), cb);
        }
        return mesh;
    }
}
