package com.dan.heidelberg.core;

/**
 * Feste Körper der Szene für die Kamera (Phase 7): Mauern, Dächer, Stämme, Kronen, Häuser, Standbilder.
 * Gelände, Wasser, Rasen, Kies und Hecken zählen nicht dazu (das Gelände prüft {@link Terrain}). Die Dreiecke
 * liegen in einem Raster aus Zellen von 8 m (Draufsicht) mit der kleinsten und größten Höhe je Zelle, damit
 * Strahlen über dem Wald fast nichts kosten. Alle Anfragen sind ohne Zustand und dürfen aus mehreren Threads
 * kommen.
 */
public final class Solids {
    public static final double CELL = 8;

    private final float[] pos;
    private final int[] idx;
    private final double x0, z0;
    private final int nx, nz;
    private final int[] start, list;
    private final float[] ymin, ymax;
    /** Dreiecke im Raster und Zellen mit Dreiecken (für die Statuszeile und die Prüfung). */
    public final int triangles, cells;

    /** Material zählt als fest? */
    public static boolean solid(int m) {
        switch (m) {
            case Mat.TERRAIN: case Mat.WATER: case Mat.RIVER: case Mat.POOL: case Mat.BASIN:
            case Mat.GRAVEL: case Mat.LAWN: case Mat.HEDGE: case Mat.ANIMAL: case Mat.SINTER: case Mat.WAX:
                return false;
            default:
                return m > 0;
        }
    }

    public Solids(Mesh me) { this(me, null); }

    /** Mit Schnitt: Dreiecke ganz auf der weggenommenen Seite zählen nicht (dort ist nichts mehr, woran die Kamera stoßen könnte). */
    public Solids(Mesh me, Schnitt.Cap cap) {
        pos = me.pos;
        idx = me.idx;
        double lx = 1e30, hx = -1e30, lz = 1e30, hz = -1e30;
        int n = 0;
        boolean[] use = new boolean[me.nt];
        for (int t = 0; t < me.nt; t++) {
            if (!solid(me.mat[t])) continue;
            if (cap != null && cap.removed(me.pos[3 * me.idx[3 * t]], 0, me.pos[3 * me.idx[3 * t] + 2]) && cap.removed(me.pos[3 * me.idx[3 * t + 1]], 0, me.pos[3 * me.idx[3 * t + 1] + 2])
                    && cap.removed(me.pos[3 * me.idx[3 * t + 2]], 0, me.pos[3 * me.idx[3 * t + 2] + 2])) continue;
            use[t] = true;
            n++;
            for (int k = 0; k < 3; k++) {
                int v = idx[3 * t + k] * 3;
                lx = Math.min(lx, pos[v]); hx = Math.max(hx, pos[v]);
                lz = Math.min(lz, pos[v + 2]); hz = Math.max(hz, pos[v + 2]);
            }
        }
        x0 = Math.floor(lx / CELL) * CELL - CELL;
        z0 = Math.floor(lz / CELL) * CELL - CELL;
        nx = (int) Math.ceil((hx - x0) / CELL) + 2;
        nz = (int) Math.ceil((hz - z0) / CELL) + 2;
        int[] cnt = new int[nx * nz + 1];
        ymin = new float[nx * nz];
        ymax = new float[nx * nz];
        java.util.Arrays.fill(ymin, Float.MAX_VALUE);
        java.util.Arrays.fill(ymax, -Float.MAX_VALUE);
        // zwei Durchgänge: zählen, dann füllen
        int[] lst = null;
        for (int pass = 0; pass < 2; pass++) {
            int[] fill = null;
            if (pass == 1) {
                for (int i = 0; i < nx * nz; i++) cnt[i + 1] += cnt[i];
                fill = new int[nx * nz];
            }
            for (int t = 0; t < me.nt; t++) {
                if (!use[t]) continue;
                int a = idx[3 * t] * 3, b = idx[3 * t + 1] * 3, c = idx[3 * t + 2] * 3;
                float tx0 = Math.min(pos[a], Math.min(pos[b], pos[c])), tx1 = Math.max(pos[a], Math.max(pos[b], pos[c]));
                float tz0 = Math.min(pos[a + 2], Math.min(pos[b + 2], pos[c + 2])), tz1 = Math.max(pos[a + 2], Math.max(pos[b + 2], pos[c + 2]));
                float ty0 = Math.min(pos[a + 1], Math.min(pos[b + 1], pos[c + 1])), ty1 = Math.max(pos[a + 1], Math.max(pos[b + 1], pos[c + 1]));
                int i0 = (int) ((tx0 - x0) / CELL), i1 = (int) ((tx1 - x0) / CELL), j0 = (int) ((tz0 - z0) / CELL), j1 = (int) ((tz1 - z0) / CELL);
                for (int j = j0; j <= j1; j++) {
                    for (int i = i0; i <= i1; i++) {
                        int cell = j * nx + i;
                        if (pass == 0) {
                            cnt[cell + 1]++;
                            if (ty0 < ymin[cell]) ymin[cell] = ty0;
                            if (ty1 > ymax[cell]) ymax[cell] = ty1;
                        } else {
                            // cnt[] ist jetzt die Anfangsposition je Zelle
                            lst[cnt[cell] + fill[cell]++] = t;
                        }
                    }
                }
            }
            if (pass == 0) {
                int total = 0;
                for (int i = 1; i <= nx * nz; i++) total += cnt[i];
                lst = new int[total];
            }
        }
        start = cnt;
        list = lst;
        triangles = n;
        int c = 0;
        for (int i = 0; i < nx * nz; i++) if (start[i + 1] > start[i]) c++;
        cells = c;
    }

    private int cellAt(double x, double z) {
        int i = (int) Math.floor((x - x0) / CELL), j = (int) Math.floor((z - z0) / CELL);
        if (i < 0 || j < 0 || i >= nx || j >= nz) return -1;
        return j * nx + i;
    }

    // ------------------------------------------------------------ Abstand zu einem Dreieck

    /** Nächster Punkt des Dreiecks t zu p nach out[0..2]; liefert den Abstand zum Quadrat. */
    private double closest(int t, double px, double py, double pz, double[] out) {
        int ia = idx[3 * t] * 3, ib = idx[3 * t + 1] * 3, ic = idx[3 * t + 2] * 3;
        double ax = pos[ia], ay = pos[ia + 1], az = pos[ia + 2];
        double bx = pos[ib], by = pos[ib + 1], bz = pos[ib + 2];
        double cx = pos[ic], cy = pos[ic + 1], cz = pos[ic + 2];
        double abx = bx - ax, aby = by - ay, abz = bz - az, acx = cx - ax, acy = cy - ay, acz = cz - az;
        double apx = px - ax, apy = py - ay, apz = pz - az;
        double d1 = abx * apx + aby * apy + abz * apz, d2 = acx * apx + acy * apy + acz * apz;
        double qx, qy, qz;
        if (d1 <= 0 && d2 <= 0) { qx = ax; qy = ay; qz = az; }
        else {
            double bpx = px - bx, bpy = py - by, bpz = pz - bz;
            double d3 = abx * bpx + aby * bpy + abz * bpz, d4 = acx * bpx + acy * bpy + acz * bpz;
            if (d3 >= 0 && d4 <= d3) { qx = bx; qy = by; qz = bz; }
            else {
                double vc = d1 * d4 - d3 * d2;
                if (vc <= 0 && d1 >= 0 && d3 <= 0) {
                    double v = d1 / (d1 - d3);
                    qx = ax + abx * v; qy = ay + aby * v; qz = az + abz * v;
                } else {
                    double cpx = px - cx, cpy = py - cy, cpz = pz - cz;
                    double d5 = abx * cpx + aby * cpy + abz * cpz, d6 = acx * cpx + acy * cpy + acz * cpz;
                    if (d6 >= 0 && d5 <= d6) { qx = cx; qy = cy; qz = cz; }
                    else {
                        double vb = d5 * d2 - d1 * d6;
                        if (vb <= 0 && d2 >= 0 && d6 <= 0) {
                            double w = d2 / (d2 - d6);
                            qx = ax + acx * w; qy = ay + acy * w; qz = az + acz * w;
                        } else {
                            double va = d3 * d6 - d5 * d4;
                            if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
                                double w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
                                qx = bx + (cx - bx) * w; qy = by + (cy - by) * w; qz = bz + (cz - bz) * w;
                            } else {
                                double den = 1 / (va + vb + vc), v = vb * den, w = vc * den;
                                qx = ax + abx * v + acx * w; qy = ay + aby * v + acy * w; qz = az + abz * v + acz * w;
                            }
                        }
                    }
                }
            }
        }
        out[0] = qx; out[1] = qy; out[2] = qz;
        double dx = px - qx, dy = py - qy, dz = pz - qz;
        return dx * dx + dy * dy + dz * dz;
    }

    /** Kleinster Abstand von p zu einem festen Dreieck innerhalb von maxD (sonst maxD); nearest bekommt den Punkt. */
    public double clearance(double px, double py, double pz, double maxD, double[] nearest) {
        double best = maxD * maxD;
        double[] q = new double[3];
        int i0 = (int) Math.floor((px - maxD - x0) / CELL), i1 = (int) Math.floor((px + maxD - x0) / CELL);
        int j0 = (int) Math.floor((pz - maxD - z0) / CELL), j1 = (int) Math.floor((pz + maxD - z0) / CELL);
        for (int j = Math.max(0, j0); j <= Math.min(nz - 1, j1); j++) {
            for (int i = Math.max(0, i0); i <= Math.min(nx - 1, i1); i++) {
                int c = j * nx + i;
                if (start[c + 1] == start[c]) continue;
                if (py + maxD < ymin[c] || py - maxD > ymax[c]) continue;
                for (int k = start[c]; k < start[c + 1]; k++) {
                    double d = closest(list[k], px, py, pz, q);
                    if (d < best) {
                        best = d;
                        if (nearest != null) { nearest[0] = q[0]; nearest[1] = q[1]; nearest[2] = q[2]; }
                    }
                }
            }
        }
        return Math.sqrt(best);
    }

    /**
     * Schiebt p aus allen festen Körpern heraus, bis der Abstand r beträgt (gleitet an Wänden entlang).
     * Liefert true, wenn p bewegt wurde.
     */
    public boolean push(double[] p, double r) {
        boolean moved = false;
        double[] q = new double[3];
        for (int it = 0; it < 6; it++) {
            double d = clearance(p[0], p[1], p[2], r, q);
            if (d >= r - 1e-4) break;
            double dx = p[0] - q[0], dy = p[1] - q[1], dz = p[2] - q[2];
            double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (l < 1e-6) {
                // genau auf der Fläche: nach oben schieben
                dx = 0; dy = 1; dz = 0; l = 1;
            }
            double k = (r - d) / l + 1e-3;
            p[0] += dx * k; p[1] += dy * k; p[2] += dz * k;
            moved = true;
        }
        return moved;
    }

    // ------------------------------------------------------------ Strahl

    /**
     * Erster Treffer des Strahls (o + t·d, d Einheitsvektor) bis maxT oder −1. Läuft Zelle für Zelle (Draufsicht)
     * und übergeht Zellen, über oder unter denen der Strahl bleibt.
     */
    public double ray(double ox, double oy, double oz, double dx, double dy, double dz, double maxT) {
        double best = maxT;
        boolean hit = false;
        // Draufsicht-Durchlauf nach Amanatides und Woo
        double px = ox, pz = oz;
        int i = (int) Math.floor((px - x0) / CELL), j = (int) Math.floor((pz - z0) / CELL);
        int si = dx > 0 ? 1 : -1, sj = dz > 0 ? 1 : -1;
        double tMaxX = Math.abs(dx) < 1e-12 ? Double.MAX_VALUE : ((dx > 0 ? (i + 1) : i) * CELL + x0 - ox) / dx;
        double tMaxZ = Math.abs(dz) < 1e-12 ? Double.MAX_VALUE : ((dz > 0 ? (j + 1) : j) * CELL + z0 - oz) / dz;
        double tDx = Math.abs(dx) < 1e-12 ? Double.MAX_VALUE : CELL / Math.abs(dx);
        double tDz = Math.abs(dz) < 1e-12 ? Double.MAX_VALUE : CELL / Math.abs(dz);
        double t = 0;
        for (int guard = 0; guard < 20000 && t <= best; guard++) {
            if (i >= 0 && j >= 0 && i < nx && j < nz) {
                int c = j * nx + i;
                double tEnd = Math.min(Math.min(tMaxX, tMaxZ), best);
                if (start[c + 1] > start[c]) {
                    double ya = oy + dy * t, yb = oy + dy * tEnd;
                    if (Math.max(ya, yb) >= ymin[c] - 0.01 && Math.min(ya, yb) <= ymax[c] + 0.01) {
                        for (int k = start[c]; k < start[c + 1]; k++) {
                            double h = hitTri(list[k], ox, oy, oz, dx, dy, dz);
                            if (h >= 0 && h < best) { best = h; hit = true; }
                        }
                    }
                }
            } else if (t > 0) {
                // außerhalb des Rasters nach dem Eintritt: fertig, wenn der Strahl wegläuft
                if ((i < 0 && si < 0) || (j < 0 && sj < 0) || (i >= nx && si > 0) || (j >= nz && sj > 0)) break;
            }
            if (tMaxX < tMaxZ) { t = tMaxX; tMaxX += tDx; i += si; } else { t = tMaxZ; tMaxZ += tDz; j += sj; }
        }
        return hit ? best : -1;
    }

    private double hitTri(int t, double ox, double oy, double oz, double dx, double dy, double dz) {
        int ia = idx[3 * t] * 3, ib = idx[3 * t + 1] * 3, ic = idx[3 * t + 2] * 3;
        double ax = pos[ia], ay = pos[ia + 1], az = pos[ia + 2];
        double e1x = pos[ib] - ax, e1y = pos[ib + 1] - ay, e1z = pos[ib + 2] - az;
        double e2x = pos[ic] - ax, e2y = pos[ic + 1] - ay, e2z = pos[ic + 2] - az;
        double px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
        double det = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(det) < 1e-12) return -1;
        double inv = 1 / det;
        double tx = ox - ax, ty = oy - ay, tz = oz - az;
        double u = (tx * px + ty * py + tz * pz) * inv;
        if (u < 0 || u > 1) return -1;
        double qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        double v = (dx * qx + dy * qy + dz * qz) * inv;
        if (v < 0 || u + v > 1) return -1;
        return (e2x * qx + e2y * qy + e2z * qz) * inv;
    }

    /**
     * Wie weit darf eine Kugel vom Radius r entlang des Strahls von o aus laufen (höchstens maxD)? Auf den
     * ersten {@code near} Metern wird mit der Kugel geprüft, danach mit dem Strahl.
     */
    public double free(double ox, double oy, double oz, double dx, double dy, double dz, double maxD, double r) {
        double lim = maxD;
        double h = ray(ox, oy, oz, dx, dy, dz, maxD);
        if (h >= 0) lim = Math.max(0, h - r);
        double near = Math.min(lim, 40);
        double step = Math.max(0.35, r * 0.6);
        for (double s = step; s <= near; s += step) {
            double c = clearance(ox + dx * s, oy + dy * s, oz + dz * s, r, null);
            if (c < r * 0.999) return Math.max(0, s - step);
        }
        return lim;
    }

    /** Ist die Strecke a→b für eine Kugel vom Radius r frei? */
    public boolean segmentFree(double ax, double ay, double az, double bx, double by, double bz, double r) {
        double dx = bx - ax, dy = by - ay, dz = bz - az, l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (l < 1e-9) return clearance(ax, ay, az, r, null) >= r * 0.999;
        if (clearance(ax, ay, az, r, null) < r * 0.999) return false;
        return free(ax, ay, az, dx / l, dy / l, dz / l, l, r) >= l - 1e-6;
    }

    /**
     * Bewegt p nach (tx, ty, tz), ohne durch feste Körper zu gehen: ist die Strecke frei, springt p ans Ziel;
     * sonst läuft es bis vor das Hindernis und gleitet in kleinen Schritten an ihm entlang. Liefert true, wenn p
     * das Ziel nicht ganz erreicht hat.
     */
    public boolean move(double[] p, double tx, double ty, double tz, double r) {
        double dx = tx - p[0], dy = ty - p[1], dz = tz - p[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-9) { push(p, r); return false; }
        dx /= len; dy /= len; dz /= len;
        double lim = free(p[0], p[1], p[2], dx, dy, dz, len, r);
        if (lim >= len - 1e-6) {
            p[0] = tx; p[1] = ty; p[2] = tz;
            push(p, r);
            return false;
        }
        p[0] += dx * lim; p[1] += dy * lim; p[2] += dz * lim;
        double rest = Math.min(len - lim, 1.2);
        int n = (int) Math.ceil(rest / 0.4);
        double st = rest / n;
        for (int i = 0; i < n; i++) {
            p[0] += dx * st; p[1] += dy * st; p[2] += dz * st;
            push(p, r);
        }
        return true;
    }
}
